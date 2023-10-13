package me.cortex.vulkanite.client.rendering.srp;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.client.rendering.srp.lua.LuaContextHost;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class test3 {
    public static byte[] load(String path) {
        try {
            return Files.readAllBytes(new File(path).toPath());
        } catch (
                IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Vulkanite.INSTANCE.getCtx();

        var host = new LuaContextHost(test3::load);
        host.loadRunScript("test.lua");
        System.err.println("Done");
        Thread.sleep(1000);
        Vulkanite.INSTANCE.destroy();
        System.gc();
    }
}
