package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.acceleration.AccelerationManager;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.compat.IVGBuffer;
import me.cortex.vulkanite.compat.IVGImage;
import me.cortex.vulkanite.compat.RaytracingShaderSet;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.base.VRegistry;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.descriptors.DescriptorUpdateBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSet;
import me.cortex.vulkanite.lib.memory.PoolLinearAllocator;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.memory.VGImage;
import me.cortex.vulkanite.lib.memory.VImage;
import me.cortex.vulkanite.lib.other.VImageView;
import me.cortex.vulkanite.lib.other.VSampler;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.cortex.vulkanite.lib.pipeline.RaytracePipelineBuilder;
import me.cortex.vulkanite.lib.pipeline.VRaytracePipeline;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;
import me.cortex.vulkanite.mixin.iris.MixinCelestialUniforms;
import me.cortex.vulkanite.mixin.iris.MixinCommonUniforms;
import net.coderbot.iris.gl.buffer.ShaderStorageBuffer;
import net.coderbot.iris.texture.pbr.PBRTextureHolder;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.opengl.EXTSemaphore.GL_LAYOUT_GENERAL_EXT;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glFlush;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

public class VulkanPipeline {
    private final VContext ctx;
    private final AccelerationManager accelerationManager;

    private record RtPipeline(VRef<VRaytracePipeline> pipeline, int commonSet, int geomSet, int customTexSet, int ssboSet) {}
    private final ArrayList<RtPipeline> raytracePipelines = new ArrayList<>();

    private final VRef<VSampler> sampler;
    private final VRef<VSampler> ctexSampler;

    private final SharedImageViewTracker[] irisRenderTargetViews;
    private final SharedImageViewTracker[] customTextureViews;
    private final SharedImageViewTracker blockAtlasView;
    private final SharedImageViewTracker blockAtlasNormalView;
    private final SharedImageViewTracker blockAtlasSpecularView;

    private final VRef<VImage> placeholderSpecular;
    private final VRef<VImageView> placeholderSpecularView;
    private final VRef<VImage> placeholderNormals;
    private final VRef<VImageView> placeholderNormalsView;

    private final int maxIrisRenderTargets = 16;

    private final boolean supportsEntities;

    private final PoolLinearAllocator uboAllocator;

    public VulkanPipeline(VContext ctx, AccelerationManager accelerationManager, RaytracingShaderSet[] passes, int[] ssboIds, List<VRef<VGImage>> customTextures) {
        this.ctx = ctx;
        this.accelerationManager = accelerationManager;

        {
            this.customTextureViews = new SharedImageViewTracker[customTextures.size()];
            for (int i = 0; i < customTextures.size(); i++) {
                int index = i;
                this.customTextureViews[i] = new SharedImageViewTracker(ctx, () -> new VRef<>(customTextures.get(index).get()));
            }

            this.irisRenderTargetViews = new SharedImageViewTracker[maxIrisRenderTargets];
            for (int i = 0; i < maxIrisRenderTargets; i++) {
                this.irisRenderTargetViews[i] = new SharedImageViewTracker(ctx, null);
            }
            this.blockAtlasView = new SharedImageViewTracker(ctx, () -> {
                AbstractTexture blockAtlas = MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft", "textures/atlas/blocks.png"));
                return ((IVGImage) blockAtlas).getVGImage();
            });
            this.blockAtlasNormalView = new SharedImageViewTracker(ctx, () -> {
                AbstractTexture blockAtlas = MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft", "textures/atlas/blocks.png"));
                PBRTextureHolder holder = PBRTextureManager.INSTANCE.getOrLoadHolder(blockAtlas.getGlId());//((TextureAtlasExtension)blockAtlas).getPBRHolder()
                return ((IVGImage) holder.getNormalTexture()).getVGImage();
            });
            this.blockAtlasSpecularView = new SharedImageViewTracker(ctx, () -> {
                AbstractTexture blockAtlas = MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft", "textures/atlas/blocks.png"));
                PBRTextureHolder holder = PBRTextureManager.INSTANCE.getOrLoadHolder(blockAtlas.getGlId());//((TextureAtlasExtension)blockAtlas).getPBRHolder()
                return ((IVGImage) holder.getSpecularTexture()).getVGImage();
            });
            this.placeholderSpecular = ctx.memory.createImage2D(4, 4, 1, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            this.placeholderSpecularView = VImageView.create(ctx, placeholderSpecular);
            this.placeholderNormals = ctx.memory.createImage2D(4, 4, 1, VK_FORMAT_R32G32B32A32_SFLOAT, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            this.placeholderNormalsView = VImageView.create(ctx, placeholderNormals);

            try (var stack = stackPush()) {
                var initZeros = stack.callocInt(4 * 4);
                var initNormals = stack.mallocFloat(4 * 4 * 4);
                for (int i = 0; i < 4 * 4; i++) {
                    initNormals.put(new float[]{0.5f, 0.5f, 1.0f, 1.0f});
                }
                initNormals.rewind();

                ctx.cmd.executeWait(cmd -> {
                    cmd.encodeImageTransition(placeholderSpecular, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, 1);
                    cmd.encodeImageTransition(placeholderNormals, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, 1);
                    cmd.encodeImageUpload(ctx.memory, MemoryUtil.memAddress(initZeros), placeholderSpecular, initZeros.capacity() * 4L, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                    cmd.encodeImageUpload(ctx.memory, MemoryUtil.memAddress(initNormals), placeholderNormals, initNormals.capacity() * 4L, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                    cmd.encodeImageTransition(placeholderSpecular, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, 1);
                    cmd.encodeImageTransition(placeholderNormals, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, 1);
                });
            }
        }

        this.sampler = VSampler.create(ctx, a -> a.magFilter(VK_FILTER_NEAREST)
                .minFilter(VK_FILTER_NEAREST)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .compareOp(VK_COMPARE_OP_NEVER)
                .maxLod(1)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .maxAnisotropy(1.0f));

        this.ctexSampler = VSampler.create(ctx, a -> a.magFilter(VK_FILTER_LINEAR)
                .minFilter(VK_FILTER_LINEAR)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .compareOp(VK_COMPARE_OP_NEVER)
                .maxLod(1)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .maxAnisotropy(1.0f));

        this.uboAllocator = new PoolLinearAllocator(ctx, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                32 * 1024,
                0, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);

        if (passes == null) {
            supportsEntities = false;
            return;
        }

        boolean supportsEntitiesT = true;
        for (var pass : passes) {
            if (pass.getRayHitCount() == 1) {
                supportsEntitiesT = false;
                break;
            }
        }
        supportsEntities = supportsEntitiesT;
        try {
            var commonSetExpected = new ShaderReflection.Set(new ShaderReflection.Binding[]{
                    new ShaderReflection.Binding("", 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 0, false),
                    new ShaderReflection.Binding("", 1, VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR, 0, false),
                    new ShaderReflection.Binding("", 3, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, false),
                    new ShaderReflection.Binding("", 4, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, false),
                    new ShaderReflection.Binding("", 5, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, false),
                    new ShaderReflection.Binding("", 6, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, maxIrisRenderTargets, false),
            });

            var geomSetExpected = new ShaderReflection.Set(new ShaderReflection.Binding[]{
                    new ShaderReflection.Binding("", 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, true)
            });

            ArrayList<ShaderReflection.Binding> customTexBindings = new ArrayList<>();
            for (int i = 0; i < customTextureViews.length; i++) {
                customTexBindings.add(new ShaderReflection.Binding("", i, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, false));
            }
            var customTexSetExpected = new ShaderReflection.Set(customTexBindings);

            ArrayList<ShaderReflection.Binding> ssboBindings = new ArrayList<>();
            for (int id : ssboIds) {
                ssboBindings.add(new ShaderReflection.Binding("", id, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 0, false));
            }
            var ssboSetExpected = new ShaderReflection.Set(ssboBindings);

            for (int i = 0; i < passes.length; i++) {
                var builder = new RaytracePipelineBuilder();
                passes[i].apply(builder);
                var pipe = builder.build(ctx, 1);

                // Validate the layout
                int commonSet = -1;
                int geomSet = -1;
                int customTexSet = -1;
                int ssboSet = -1;

                for (int setIdx = 0; setIdx < pipe.get().reflection.getNSets(); setIdx++) {
                    var set = pipe.get().reflection.getSet(setIdx);
                    if (set.validate(commonSetExpected)) {
                        commonSet = setIdx;
                    } else if (set.validate(geomSetExpected)) {
                        geomSet = setIdx;
                    } else if (set.validate(customTexSetExpected)) {
                        customTexSet = setIdx;
                    } else if (set.validate(ssboSetExpected)) {
                        ssboSet = setIdx;
                    } else {
                        throw new RuntimeException("Raytracing pipeline " + i + " has an unexpected descriptor set layout at set " + setIdx);
                    }
                }

                raytracePipelines.add(new RtPipeline(pipe, commonSet, geomSet, customTexSet, ssboSet));
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());

            e.printStackTrace();
            destory();
            throw new RuntimeException(e);
        }
    }

    private final EntityCapture capture = new EntityCapture();
    private void buildEntities() {
        accelerationManager.setEntityData(supportsEntities?capture.capture(CapturedRenderingState.INSTANCE.getTickDelta(), MinecraftClient.getInstance().world):null);
    }

    public void renderPostShadows(List<VRef<VGImage>> vgOutImgs, Camera camera, ShaderStorageBuffer[] ssbos, MixinCelestialUniforms celestialUniforms) {
        ctx.cmd.newFrame();

        System.out.println(VRegistry.INSTANCE.dumpStats());

        buildEntities();

        PBRTextureManager.notifyPBRTexturesChanged();

        var in = ctx.sync.createSharedBinarySemaphore();
        var outImgsGlIds = vgOutImgs.stream().mapToInt(i -> i.get().glId).toArray();
        var outImgsGlLayouts = vgOutImgs.stream().mapToInt(i -> GL_LAYOUT_GENERAL_EXT).toArray();
        in.get().glSignal(new int[0], outImgsGlIds, outImgsGlLayouts);
        // glFlush();

        var cmdRef = ctx.cmd.getSingleUsePool().createCommandBuffer();
        var cmd = cmdRef.get();

        var tlas = accelerationManager.buildTLAS(0, cmd);

        if (tlas == null) {
            glFinish();
            return;
        }

        var outImgs = vgOutImgs.stream().map(i -> new VRef<VImage>(i.get())).toList();

        var out = ctx.sync.createSharedBinarySemaphore();

        var vref_in = new VRef<VSemaphore>(in.get());
        var vref_out = new VRef<VSemaphore>(out.get());

        var uboBuffer = uboAllocator.allocate(1024);
        {
            long ptr = uboBuffer.buffer().get().map();
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
            uboBuffer.buffer().get().unmap();
            uboBuffer.buffer().get().flush();

            // Call getView() on shared image view trackers to ensure they are created
            blockAtlasView.getView();
            blockAtlasNormalView.getView();
            blockAtlasSpecularView.getView();
            for (var v : customTextureViews) {
                v.getView();
            }

            {
                // Put barriers on images & transition to the optimal layout
                // These layouts also need to match the descriptor sets
                for (var img : outImgs) {
                    cmd.encodeImageTransition(img, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
                }
                cmd.encodeImageTransition(blockAtlasView.getImage(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);

                var image = blockAtlasNormalView.getImage();
                if (image != null) cmd.encodeImageTransition(image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
                image = blockAtlasSpecularView.getImage();
                if (image != null) cmd.encodeImageTransition(image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);

                for(SharedImageViewTracker customtexView : customTextureViews) {
                   cmd.encodeImageTransition(customtexView.getImage(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
                }
            }

            for (var record : raytracePipelines) {
                var pipeline = record.pipeline;
                cmd.bindRT(pipeline);
                var layouts = pipeline.get().reflection.getLayouts(); // Should be cached already
                var sets = new ArrayList<VRef<VDescriptorSet>>(layouts.size());
                for (int i = 0; i < layouts.size(); i++) {
                    sets.add(null); // Well, LOL, can't use Arrays.toList because it's generic
                }
                if (record.commonSet != -1) {
                    var commonSet = Vulkanite.INSTANCE.getPoolByLayout(layouts.get(record.commonSet)).get().allocateSet();

                    var updater = new DescriptorUpdateBuilder(ctx, pipeline.get().reflection.getSet(record.commonSet))
                            .set(commonSet)
                            .uniform(0, uboBuffer.buffer(), uboBuffer.offset(), uboBuffer.size())
                            .acceleration(1, tlas)
                            .imageSampler(3, blockAtlasView.getView(), sampler)
                            .imageSampler(4,
                                    blockAtlasNormalView.getView() != null ? blockAtlasNormalView.getView()
                                            : placeholderNormalsView,
                                    sampler)
                            .imageSampler(5,
                                    blockAtlasSpecularView.getView() != null ? blockAtlasSpecularView.getView()
                                            : placeholderSpecularView,
                                    sampler);
                    List<VRef<VImageView>> outImgViewList = new ArrayList<>(outImgs.size());
                    for (int i = 0; i < outImgs.size(); i++) {
                        int index = i;
                        outImgViewList.add(irisRenderTargetViews[i].getView(() -> vgOutImgs.get(index)));
                    }
                    updater.imageStore(6, 0, outImgViewList);
                    updater.apply();

                    sets.set(record.commonSet, commonSet);
                }
                if (record.geomSet != -1) {
                    sets.set(record.geomSet, accelerationManager.getGeometrySet());
                }
                if (record.customTexSet != -1) {
                    var ctexSet = Vulkanite.INSTANCE.getPoolByLayout(layouts.get(record.customTexSet)).get().allocateSet();

                    var updater = new DescriptorUpdateBuilder(ctx, pipeline.get().reflection.getSet(record.customTexSet))
                            .set(ctexSet);
                    for (int i = 0; i < customTextureViews.length; i++) {
                        updater.imageSampler(i, customTextureViews[i].getView(), ctexSampler);
                    }
                    updater.apply();

                    sets.set(record.customTexSet, ctexSet);
                }
                if (record.ssboSet != -1) {
                    var ssboSet = Vulkanite.INSTANCE.getPoolByLayout(layouts.get(record.ssboSet)).get().allocateSet();

                    var updater = new DescriptorUpdateBuilder(ctx, pipeline.get().reflection.getSet(record.ssboSet))
                            .set(ssboSet);
                    for (ShaderStorageBuffer ssbo : ssbos) {
                        updater.buffer(ssbo.getIndex(), new VRef<>(((IVGBuffer) ssbo).getBuffer().get()));
                    }
                    updater.apply();

                    sets.set(record.ssboSet, ssboSet);
                }
                cmd.bindDSet(sets);
                cmd.traceRays(outImgs.get(0).get().width, outImgs.get(0).get().height, 1);
                sets.forEach(VRef::close);

                // Barrier on the output images
                for (var img : outImgs) {
                    cmd.encodeImageTransition(img, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
                }
            }

            {
                // Transition images back to general layout (for OpenGL)
                cmd.encodeImageTransition(blockAtlasView.getImage(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);

                var image = blockAtlasNormalView.getImage();
                if (image != null) cmd.encodeImageTransition(image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
                image = blockAtlasSpecularView.getImage();
                if (image != null) cmd.encodeImageTransition(image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);

                for(SharedImageViewTracker customtexView : customTextureViews) {
                   cmd.encodeImageTransition(customtexView.getImage(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_ASPECT_COLOR_BIT, VK_REMAINING_MIP_LEVELS);
                }
            }

            ctx.cmd.submit(0, cmdRef, Arrays.asList(vref_in), Arrays.asList(vref_out), null);
        }

        cmdRef.close();
        tlas.close();

        out.get().glWait(new int[0], outImgsGlIds, outImgsGlLayouts);
        // glFlush();
        vref_in.close();
        vref_out.close();
        in.close();
        out.close();
    }

    public void destory() {
        vkDeviceWaitIdle(ctx.device);
        ctx.cmd.newFrame();
        System.gc();
    }


}
