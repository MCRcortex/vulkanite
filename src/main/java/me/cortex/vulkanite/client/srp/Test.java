package me.cortex.vulkanite.client.srp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Test {
    public static byte[] load(String path) {
        try {
            return Files.readAllBytes(new File(path).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void main(String[] args) {
        var srp = new ScriptablePipelineHost(Test::load);
        srp.load("test.lua");
    }
}
