package me.cortex.vulkanite.compat;

import me.jellysquid.mods.sodium.client.util.NativeBuffer;

public record GeometryData(int quadCount, NativeBuffer data) {
}
