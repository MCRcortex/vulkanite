package me.cortex.vulkanite.lib.memory;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;

public class VGImage extends VImage {
    public final int glId;
    private final int glMemObj;
    public final int glFormat;
    private final HANDLE handle;

    VGImage(VmaAllocator.ImageAllocation allocation, int width, int height, int mipLayers, int format, int glFormat, int glId, int glMemObj, long handle) {
        super(allocation, width, height, mipLayers, format);
        this.glId = glId;
        this.glMemObj = glMemObj;
        this.glFormat = glFormat;
        this.handle = new HANDLE(new Pointer(handle));
    }

    public void free() {
        Kernel32.INSTANCE.CloseHandle(this.handle);
      
        glDeleteTextures(glId);
        glDeleteMemoryObjectsEXT(glMemObj);
        _CHECK_GL_ERROR_();
        super.free();
    }
}
