package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.graph.GraphExecutor;
import me.cortex.vulkanite.client.rendering.srp.lua.LuaContextHost;
import me.cortex.vulkanite.client.rendering.srp.lua.LuaExternalObjects;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VGImage;
import me.cortex.vulkanite.lib.other.sync.VGSemaphore;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.cortex.vulkanite.mixin.iris.MixinCelestialUniforms;
import me.cortex.vulkanite.mixin.iris.MixinCommonUniforms;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CelestialUniforms;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.luaj.vm2.Lua;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.opengl.EXTSemaphore.GL_LAYOUT_GENERAL_EXT;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glFlush;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

public class VkPipeline2 {
    private final VContext ctx;
    private final AccelerationManager accelerationManager;
    private final VCommandPool singleUsePool;
    private final LuaContextHost pipeline;
    private final EntityCapture capture = new EntityCapture();

    public VkPipeline2(VContext ctx, LuaContextHost pipeline, AccelerationManager accelerationManager) {
        this.ctx = ctx;
        this.accelerationManager = accelerationManager;
        this.singleUsePool = ctx.cmd.createSingleUsePool();

        pipeline.loadScript("srp.lua");
        pipeline.run();
        this.pipeline = pipeline;
    }

    private void buildEntities() {
        accelerationManager.setEntityData(capture.capture(CapturedRenderingState.INSTANCE.getTickDelta(), MinecraftClient.getInstance().world));
    }

    private Camera camera;
    private MixinCelestialUniforms celestialUniforms;

    public void setup(Camera camera, MixinCelestialUniforms celestialUniforms) {
        this.camera = camera;
        this.celestialUniforms = celestialUniforms;
    }

    private VBuffer createUBO() {
        VBuffer uboBuffer = ctx.memory.createBuffer(1024,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                0, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        long ptr = uboBuffer.map();
        MemoryUtil.memSet(ptr, 0, 1024);
        {
            ByteBuffer bb = MemoryUtil.memByteBuffer(ptr, 1024);

            Vector3f tmpv3 = new Vector3f();
            Matrix4f invProjMatrix = new Matrix4f();
            Matrix4f invViewMatrix = new Matrix4f();

            CapturedRenderingState.INSTANCE.getGbufferProjection().invert(invProjMatrix);
            new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView()).translate(camera.getPos().toVector3f().negate()).invert(invViewMatrix);

            invProjMatrix.transformProject(-1, -1, 0, 1, tmpv3).get(bb);
            invProjMatrix.transformProject(+1, -1, 0, 1, tmpv3).get(4*Float.BYTES, bb);
            invProjMatrix.transformProject(-1, +1, 0, 1, tmpv3).get(8*Float.BYTES, bb);
            invProjMatrix.transformProject(+1, +1, 0, 1, tmpv3).get(12*Float.BYTES, bb);
            invViewMatrix.get(Float.BYTES * 16, bb);

            celestialUniforms.invokeGetSunPosition().get(Float.BYTES * 32, bb);
            celestialUniforms.invokeGetMoonPosition().get(Float.BYTES * 36, bb);

            bb.putInt(Float.BYTES * 40, SystemTimeUniforms.COUNTER.getAsInt());

            int flags = MixinCommonUniforms.invokeIsEyeInWater() & 3;
            bb.putInt(Float.BYTES * 41, flags);
            bb.rewind();
        }
        uboBuffer.unmap();
        uboBuffer.flush();
        return uboBuffer;
    }

    public void execute(List<VGImage> outTextures) {
        this.buildEntities();
        this.singleUsePool.doReleases();
        PBRTextureManager.notifyPBRTexturesChanged();

        var outImgsGlIds = new int[0];
        var outImgsGlLayouts = new int[0];

        var in = this.ctx.sync.createSharedBinarySemaphore();
        in.glSignal(new int[0], outImgsGlIds, outImgsGlLayouts);
        glFlush();

        var tlasLink = ctx.sync.createBinarySemaphore();
        var tlas = accelerationManager.buildTLAS(in, tlasLink);

        if (tlas == null) {
            glFinish();
            tlasLink.free();
            in.free();
            return;
        }


        //Update the shared objects
        if (LuaExternalObjects.TERRAIN_GEOMETRY_LAYOUT.getConcrete() != accelerationManager.getGeometrySet()) {
            LuaExternalObjects.TERRAIN_GEOMETRY_LAYOUT.setConcrete(accelerationManager.getGeometrySet());
        }

        var ubo = this.createUBO();
        LuaExternalObjects.COMMON_UNIFORM_BUFFER.setConcrete(ubo);
        LuaExternalObjects.WORLD_ACCELERATION_STRUCTURE.setConcrete(tlas);
        LuaExternalObjects.BLOCK_ATLAS.setConcrete(((IVGImage)MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft", "textures/atlas/blocks.png"))).getVGImage());
        for (int i = 0; i < outTextures.size(); i++) {
            LuaExternalObjects.IRIS_IMAGES[i].setConcrete(outTextures.get(i));
        }

        var cmd = this.singleUsePool.createCommandBuffer();
        cmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        var fence = this.ctx.sync.createFence();


        GraphExecutor.execute(this.pipeline.getGraph(), cmd, fence);


        cmd.end();

        var out = ctx.sync.createSharedBinarySemaphore();
        this.ctx.cmd.submit(0, new VCmdBuff[]{cmd}, new VSemaphore[]{tlasLink}, new int[]{VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR}, new VSemaphore[]{out}, fence);

        ctx.sync.addCallback(fence, ()->{
            in.free();
            cmd.enqueueFree();
            fence.free();
            tlasLink.free();
            ubo.free();
            Vulkanite.INSTANCE.addSyncedCallback(out::free);
        });

        out.glWait(new int[0], outImgsGlIds, outImgsGlLayouts);
        glFlush();
    }

    public void destory() {
        vkDeviceWaitIdle(this.ctx.device);
        this.pipeline.destory();
        this.ctx.sync.checkFences();
        this.singleUsePool.doReleases();
        this.singleUsePool.free();
    }
}
