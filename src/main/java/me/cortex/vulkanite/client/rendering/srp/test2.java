package me.cortex.vulkanite.client.rendering.srp;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding;
import me.cortex.vulkanite.client.rendering.srp.graph.RenderGraph;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.memory.BufferCopyPass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.ComputePass;
import me.cortex.vulkanite.client.rendering.srp.graph.phase.pipeline.TracePass;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.BufferResource;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.ExternalImageResource;
import me.cortex.vulkanite.lib.shader.VShader;
import me.cortex.vulkanite.lib.shader.reflection.ShaderReflection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;

public class test2 {
    public static void main(String[] args) throws IOException {
        var shader3 = VShader.compileLoad(Vulkanite.INSTANCE.getCtx(), new String(Files.readAllBytes(new File("run/shaderpacks/testpack/shaders/ray0_0.rchit").toPath())), VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR);
        var shader1 = VShader.compileLoad(Vulkanite.INSTANCE.getCtx(), new String(Files.readAllBytes(new File("run/shaderpacks/testpack/shaders/ray0.rgen").toPath())), VK_SHADER_STAGE_RAYGEN_BIT_KHR);
        var shader2 = VShader.compileLoad(Vulkanite.INSTANCE.getCtx(), new String(Files.readAllBytes(new File("run/shaderpacks/testpack/shaders/ray0_0.rahit").toPath())), VK_SHADER_STAGE_ANY_HIT_BIT_KHR);
        var shader4 = VShader.compileLoad(Vulkanite.INSTANCE.getCtx(), new String(Files.readAllBytes(new File("run/shaderpacks/testpack/shaders/ray0_1.rchit").toPath())), VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR);
        var shader5 = VShader.compileLoad(Vulkanite.INSTANCE.getCtx(), new String(Files.readAllBytes(new File("run/shaderpacks/testpack/shaders/ray0_0.rmiss").toPath())), VK_SHADER_STAGE_MISS_BIT_KHR);
        var reflection = ShaderReflection.mergeStages(shader1.getReflection(), shader2.getReflection(), shader3.getReflection(), shader4.getReflection(), shader5.getReflection());

        System.err.println(reflection.getSets().stream().map(test2::buildLayout).toList());
    }


    private static Layout buildLayout(ShaderReflection.Set set) {
        List<LayoutBinding> bindingList = new ArrayList<>();
        for (var binding : set.bindings()) {
            bindingList.add(new LayoutBinding(binding.name(), binding.binding(), 3, binding.descriptorType(), binding.runtimeSized()?-1:binding.arraySize()));
        }
        return new Layout(false, bindingList.toArray(LayoutBinding[]::new));
    }
}
