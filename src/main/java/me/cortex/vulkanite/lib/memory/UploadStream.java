package me.cortex.vulkanite.lib.memory;

//Uses a host visible, client mapped buffer to stream data to the gpu, can use fence or something to signal once the
// data is free

import me.cortex.vulkanite.lib.cmd.VCmdBuff;

public class UploadStream {

    public long malloc(long size) {
        return -1;
    }

    public void upload(long addr, VCmdBuff cmd) {

    }
}
