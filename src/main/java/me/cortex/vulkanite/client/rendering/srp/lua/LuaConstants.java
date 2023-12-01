package me.cortex.vulkanite.client.rendering.srp.lua;


import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import org.luaj.vm2.LuaValue;

import static me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding.ACCESS_READ;
import static me.cortex.vulkanite.client.rendering.srp.api.layout.LayoutBinding.ACCESS_WRITE;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK10.*;

//Environment about the game such as block lists, biomes etc
public class LuaConstants {
    public static void addVkConstants(LuaValue env) {
        env.set("SHADER_RAY_GEN", VK_SHADER_STAGE_RAYGEN_BIT_KHR);
        env.set("SHADER_RAY_MISS", VK_SHADER_STAGE_MISS_BIT_KHR);
        env.set("SHADER_RAY_CHIT", VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR);
        env.set("SHADER_RAY_AHIT", VK_SHADER_STAGE_ANY_HIT_BIT_KHR);
        env.set("SHADER_RAY_INTER", VK_SHADER_STAGE_INTERSECTION_BIT_KHR);
        env.set("SHADER_COMPUTE", VK_SHADER_STAGE_COMPUTE_BIT);

        env.set("ACCESS_READ", ACCESS_READ);
        env.set("ACCESS_WRITE", ACCESS_WRITE);
        env.set("ACCESS_RW", ACCESS_READ|ACCESS_WRITE);

        env.set("LAYOUT_ACCELERATION", VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR);
        env.set("LAYOUT_UNIFORM_BUFFER", VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
        env.set("LAYOUT_STORAGE_BUFFER", VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
        env.set("LAYOUT_STORAGE_IMAGE", VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
        env.set("LAYOUT_IMAGE_SAMPLER", VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
    }

    public static void addWorldConstants(LuaValue env) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) {
            //If there is no world, cannot add world constants
            return;
        }
        /*
        var rm = world.getRegistryManager();
        {
            var blockRegistry = rm.get(RegistryKeys.BLOCK);
            for (var blockEntry : blockRegistry.getEntrySet()) {

                blockRegistry.getEntry(blockEntry.getKey()).get().isIn(TagK)

            }
        }
        var biomes = rm.get(RegistryKeys.BIOME).getEntrySet();
        */
    }

    public static void addConstants(LuaValue env) {
        addVkConstants(env);
        addWorldConstants(env);
    }
}