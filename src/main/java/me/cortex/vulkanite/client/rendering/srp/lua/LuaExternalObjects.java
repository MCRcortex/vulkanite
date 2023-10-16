package me.cortex.vulkanite.client.rendering.srp.lua;

import me.cortex.vulkanite.client.rendering.srp.api.layout.Layout;
import me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding;
import me.cortex.vulkanite.client.rendering.srp.graph.resource.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding.ACCESS_READ;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;

public class LuaExternalObjects {
    private static final List<Resource<?>> EXTERNAL_RESOURCES = new ArrayList<>();

    public static final ExternalBufferResource COMMON_UNIFORM_BUFFER = (ExternalBufferResource) register(new ExternalBufferResource(), "Common uniform buffer");
    public static final ExternalAccelerationResource WORLD_ACCELERATION_STRUCTURE = register(new ExternalAccelerationResource(), "World acceleration structure");
    public static final ExternalImageResource BLOCK_ATLAS = (ExternalImageResource) register(new ExternalImageResource(), "Block atlas");
    public static final ExternalImageResource BLOCK_ATLAS_NORMAL = (ExternalImageResource) register(new ExternalImageResource(), "Block atlas normals");
    public static final ExternalImageResource BLOCK_ATLAS_SPECULAR = (ExternalImageResource) register(new ExternalImageResource(), "Block atlas specular");
    public static final ExternalImageResource[] IRIS_IMAGES = IntStream.range(0, 16).mapToObj(i->(ExternalImageResource)register(new ExternalImageResource(),"Iris colortex"+i)).toArray(ExternalImageResource[]::new);
    public static final ExternalBoundDescriptorSet TERRAIN_GEOMETRY_LAYOUT = register(new ExternalBoundDescriptorSet(new Layout(new LayoutBinding(2, ACCESS_READ, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, -1))), "External acceleration terrain data layout");
    public static final ExternalBoundDescriptorSet ENTITY_DATA_LAYOUT = register(new ExternalBoundDescriptorSet(new Layout(
            new LayoutBinding(0, ACCESS_READ, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 32),
            new LayoutBinding(1, ACCESS_READ, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 32))), "External acceleration entity data layout");
    private static <T extends Resource<T>> T register(T resource, String name) {
        EXTERNAL_RESOURCES.add(resource);
        resource.name(name);
        return resource;
    }

    public static void resetExternalObjectGraph() {
        EXTERNAL_RESOURCES.forEach(Resource::resetDependencies);
    }
}
