package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.compat.RaytracingShaderSet;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.descriptors.DescriptorSetLayoutBuilder;
import me.cortex.vulkanite.lib.descriptors.DescriptorUpdateBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorPool;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VGImage;
import me.cortex.vulkanite.lib.other.VImageView;
import me.cortex.vulkanite.lib.other.VSampler;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.cortex.vulkanite.lib.pipeline.RaytracePipelineBuilder;
import me.cortex.vulkanite.lib.pipeline.VRaytracePipeline;
import me.cortex.vulkanite.lib.pipeline.VShader;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static net.coderbot.iris.uniforms.CelestialUniforms.getSunAngle;
import static net.coderbot.iris.uniforms.CelestialUniforms.isDay;
import static org.lwjgl.opengl.EXTSemaphore.GL_LAYOUT_GENERAL_EXT;
import static org.lwjgl.opengl.EXTSemaphore.glSignalSemaphoreEXT;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glFlush;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL;

public class VulkanPipeline {
    private final VContext ctx;
    private final AccelerationManager accelerationManager;
    private final VCommandPool singleUsePool;

    private VRaytracePipeline[] raytracePipelines;
    private VDescriptorSetLayout layout;
    private VDescriptorPool descriptors;

    private final VSampler sampler;

    private int fidx;

    public VulkanPipeline(VContext ctx, AccelerationManager accelerationManager, RaytracingShaderSet[] passes) {
        this.ctx = ctx;
        this.accelerationManager = accelerationManager;
        this.singleUsePool = ctx.cmd.createSingleUsePool();
        this.sampler = new VSampler(ctx, a->a.magFilter(VK_FILTER_NEAREST)
                .minFilter(VK_FILTER_NEAREST)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .compareOp(VK_COMPARE_OP_NEVER)
                .maxLod(1)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .maxAnisotropy(1.0f));

        try {
            layout = new DescriptorSetLayoutBuilder()
                    .binding(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_ALL)//camera data
                    .binding(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR, VK_SHADER_STAGE_ALL)//funni acceleration buffer
                    .binding(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_ALL)//funni buffer buffer
                    .binding(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, VK_SHADER_STAGE_ALL)//output texture
                    .binding(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_ALL)//block texture
                    .build(ctx);

            //TODO: use frameahead count instead of just... 10
            descriptors = new VDescriptorPool(ctx, VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT, 10, layout.types);
            descriptors.allocateSets(layout);

            raytracePipelines = new VRaytracePipeline[passes.length];
            for (int i = 0; i < passes.length; i++) {
                var builder = new RaytracePipelineBuilder().addLayout(layout);
                passes[i].apply(builder);
                raytracePipelines[i] = builder.build(ctx, 1);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private VImageView view;
    private VImageView blockView;

    private VSemaphore previousSemaphore;
    public void renderPostShadows(VGImage outImg, Camera camera) {

        this.singleUsePool.doReleases();
        if (view == null || outImg != view.image) {
            //TODO: free the old image with a fence or something kasjdhglkasjdg
            view = new VImageView(ctx, outImg);
        }

        var blockImage = ((IVGImage)MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft", "textures/atlas/blocks.png"))).getVGImage();
        if (blockView == null || blockView.image != blockImage) {
            //TODO: free the old image with a fence or something kasjdhglkasjdg
            blockView = new VImageView(ctx, blockImage);
        }

        //TODO:FIXME: this creates a memory leak every time this is run
        var in = ctx.sync.createSharedBinarySemaphore();
        in.glSignal(new int[0], new int[]{outImg.glId}, new int[]{GL_LAYOUT_GENERAL_EXT});
        glFlush();

        var tlasLink = ctx.sync.createBinarySemaphore();

        var tlas = accelerationManager.buildTLAS(in, tlasLink);
        if (tlas == null) {
            glFinish();
            tlasLink.free();
            in.free();
            return;
        }

        var out = ctx.sync.createSharedBinarySemaphore();
        VBuffer uboBuffer;
        VBuffer refBuffer;
        {
            uboBuffer = ctx.memory.createBufferGlobal(1024, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
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

                Vector4f position = new Vector4f(0.0F, isDay() ? 100 : -100, 0.0F, 0.0F);

                // TODO: Deduplicate / remove this function.
                Matrix4f celestial = new Matrix4f();
                celestial.identity();

                // This is the same transformation applied by renderSky, however, it's been moved to here.
                // This is because we need the result of it before it's actually performed in vanilla.
                celestial.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
                celestial.rotate(RotationAxis.POSITIVE_X.rotationDegrees(getSunAngle() * 360.0F));

                celestial.transform(position);


                Vector3f vec3 = new Vector3f(position.x(), position.y(), position.z());
                vec3.normalize();
                vec3.get(Float.BYTES * 32, bb);
            }
            uboBuffer.unmap();
            uboBuffer.flush();


            refBuffer = ctx.memory.createBuffer(1024, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,16);

            long desc = descriptors.get(fidx);

            new DescriptorUpdateBuilder(ctx, 5)
                    .set(desc)
                    .uniform(0, uboBuffer)
                    .acceleration(1, tlas)
                    .buffer(2, refBuffer)
                    .imageStore(3, view)
                    .imageSampler(4, blockView, sampler)
                    .apply();


            //TODO: dont use a single use pool for commands like this...
            var cmd = singleUsePool.createCommandBuffer();
            cmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            try (var stack = stackPush()) {
                vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR, 0, null, null,
                        VkImageMemoryBarrier.calloc(2, stack).apply(0, a->a
                                .sType$Default()
                                .image(view.image.image())
                                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                                .srcAccessMask(0)
                                .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                                .subresourceRange(e->e.levelCount(1).layerCount(1).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)))
                                .apply(1, a->a
                                        .sType$Default()
                                        .image(blockView.image.image())
                                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                        .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                                        .srcAccessMask(0)
                                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                                        .subresourceRange(e->e.levelCount(1).layerCount(1).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT))));
            }

            for (var pipeline : raytracePipelines) {
                pipeline.bind(cmd);
                pipeline.bindDSet(cmd, desc);
                pipeline.trace(cmd, outImg.width, outImg.height, 1);
            }

            cmd.end();
            var fence = ctx.sync.createFence();
            ctx.cmd.submit(0, new VCmdBuff[]{cmd}, new VSemaphore[]{tlasLink}, new int[]{VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR}, new VSemaphore[]{out}, fence);


            var semCapture = previousSemaphore;
            previousSemaphore = out;
            ctx.sync.addCallback(fence, ()->{
                tlasLink.free();
                in.free();
                cmd.enqueueFree();
                fence.free();


                //TODO: figure out how to free the out semaphore
                uboBuffer.free();
                refBuffer.free();
                if (semCapture != null) {
                    semCapture.free();
                }
            });
        }

        out.glWait(new int[0], new int[]{outImg.glId}, new int[]{GL_LAYOUT_GENERAL_EXT});
        glFlush();

        fidx++;
        fidx %= 10;

    }

    public void destory() {
        for (var pass : raytracePipelines) {
            pass.free();
        }
        layout.free();
        descriptors.free();
        singleUsePool.doReleases();
        singleUsePool.free();
        if (previousSemaphore != null) {
            previousSemaphore.free();
        }
        if (view != null) {
            view.free();
        }
        if (blockView != null) {
            blockView.free();
        }
        sampler.free();
    }
}
