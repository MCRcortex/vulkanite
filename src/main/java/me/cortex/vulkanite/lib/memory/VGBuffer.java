package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.client.Vulkanite;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.EXTMemoryObject.glDeleteMemoryObjectsEXT;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;

public class VGBuffer extends VBuffer {
    public final int glId;
    private final int glMemObj;
    private final WinNT.HANDLE handle;
    VGBuffer(VmaAllocator.BufferAllocation allocation, int glId, int glMemObj, long handle) {
        super(allocation);
        this.glId = glId;
        this.glMemObj = glMemObj;
        this.handle = new HANDLE(new Pointer(handle));
    }

    @Override
    public void free() {
        Kernel32.INSTANCE.CloseHandle(this.handle);
        glDeleteBuffers(glId);
        glDeleteMemoryObjectsEXT(glMemObj);
        _CHECK_GL_ERROR_();
        super.free();
    }
}
