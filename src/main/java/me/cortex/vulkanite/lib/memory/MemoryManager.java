package me.cortex.vulkanite.lib.memory;

import com.sun.jna.Pointer;
import com.sun.jna.platform.linux.LibC;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import me.cortex.vulkanite.client.Vulkanite;

import org.lwjgl.PointerBuffer;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.function.Function;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT;
import static org.lwjgl.opengl.EXTMemoryObjectFD.glImportMemoryFdEXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_KMT_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkCreateAccelerationStructureKHR;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.vkGetMemoryFdKHR;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT;

public class MemoryManager {
    private static final int EXTERNAL_MEMORY_HANDLE_TYPE = Vulkanite.IS_WINDOWS?VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT:VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
    private final VkDevice device;
    private final VmaAllocator allocator;
    private final boolean hasDeviceAddresses;

    private static final long sharedBlockSize = 64L << 20L; // 64 MB

    public MemoryManager(VkDevice device, boolean hasDeviceAddresses) {
        this.device = device;
        this.hasDeviceAddresses = hasDeviceAddresses;
        allocator = new VmaAllocator(device, this.hasDeviceAddresses, sharedBlockSize, EXTERNAL_MEMORY_HANDLE_TYPE);
    }

    public class ExternalMemoryTracker {
        public record HandleDescriptor(long handle, int glMemoryObj) {
        }
        public record HandleDescriptorTracked(HandleDescriptor desc, int refCount) {
        }

        // Maps VK memory to {GL memory, Native handle} tuple & with refernce counting
        private static final HashMap<Long, HandleDescriptorTracked> MEMORY_TO_HANDLES = new HashMap<>();

        // Get the GL memory associated with the given vulkan memory object
        public static int acquire(VmaAllocator.Allocation allocation, VkDevice device, boolean dedicated) {
            synchronized (MEMORY_TO_HANDLES) {
                long vkMemory = allocation.ai.deviceMemory();
                if (!MEMORY_TO_HANDLES.containsKey(vkMemory)) {
                    long nativeHandle = 0;
                    try (var stack = stackPush()) {
                        if (Vulkanite.IS_WINDOWS) {
                            PointerBuffer pb = stack.callocPointer(1);
                            _CHECK_(vkGetMemoryWin32HandleKHR(device, VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                                    .sType$Default()
                                    .memory(vkMemory)
                                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT), pb));
                            nativeHandle = pb.get(0);
                        } else {
                            IntBuffer pb = stack.callocInt(1);
                            _CHECK_(vkGetMemoryFdKHR(device, VkMemoryGetFdInfoKHR.calloc(stack)
                                    .sType$Default()
                                    .memory(vkMemory)
                                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT), pb));
                            nativeHandle = pb.get(0);
                        }
                    }

                    if (nativeHandle == 0)
                        throw new IllegalStateException();

                    int newMemoryObject = glCreateMemoryObjectsEXT();
                    // Everything larger than the shared block size must be dedicated allocation
                    long memorySize = dedicated ? (allocation.ai.offset() + allocation.ai.size()) : sharedBlockSize;

                    if (dedicated) {
                        // https://registry.khronos.org/OpenGL/extensions/EXT/EXT_external_objects.txt
                        // Section 6.2
                        glMemoryObjectParameteriEXT(newMemoryObject, GL_DEDICATED_MEMORY_OBJECT_EXT, GL_TRUE);
                        _CHECK_GL_ERROR_();
                    }

                    if (Vulkanite.IS_WINDOWS) {
                        glImportMemoryWin32HandleEXT(newMemoryObject,
                                memorySize,
                                GL_HANDLE_TYPE_OPAQUE_WIN32_KMT_EXT, nativeHandle);
                        _CHECK_GL_ERROR_();
                    } else {
                        glImportMemoryFdEXT(newMemoryObject, memorySize,
                                GL_HANDLE_TYPE_OPAQUE_FD_EXT, (int) nativeHandle);
                        _CHECK_GL_ERROR_();
                    }

                    if (newMemoryObject == 0)
                        throw new IllegalStateException();

                    MEMORY_TO_HANDLES.put(vkMemory,
                            new HandleDescriptorTracked(new HandleDescriptor(nativeHandle, newMemoryObject), 0));
                }

                var tracked = MEMORY_TO_HANDLES.get(vkMemory);
                MEMORY_TO_HANDLES.put(vkMemory, new HandleDescriptorTracked(tracked.desc, tracked.refCount + 1));

                return tracked.desc.glMemoryObj;
            }
        }

        public static void release(long memory) {
            synchronized (MEMORY_TO_HANDLES) {
                var tracked = MEMORY_TO_HANDLES.get(memory);
                if (tracked.refCount <= 0) {
                    throw new IllegalStateException();
                }
                if (tracked.refCount == 1) {
                    glDeleteMemoryObjectsEXT(tracked.desc.glMemoryObj);
                    _CHECK_GL_ERROR_();
                    if (Vulkanite.IS_WINDOWS) {
                        // if (!Kernel32.INSTANCE.CloseHandle(new WinNT.HANDLE(new Pointer(tracked.desc.handle)))) {
                        //     int error = Kernel32.INSTANCE.GetLastError();
                        //     System.err.println("STATE MIGHT BE BROKEN! Failed to close handle: " + error);
                        //     throw new IllegalStateException();
                        // }
                    } else {
                        int code = 0;
                        if ((code = LibC.INSTANCE.close((int) tracked.desc.handle)) != 0) {
                            System.err.println("STATE MIGHT BE BROKEN! Failed to close FD: " + code);
                            throw new IllegalStateException();
                        }
                    }
                    MEMORY_TO_HANDLES.remove(memory);
                } else {
                    MEMORY_TO_HANDLES.put(memory, new HandleDescriptorTracked(tracked.desc, tracked.refCount - 1));
                }
            }
        }
    };

    public VGBuffer createSharedBuffer(long size, int usage, int properties) {
        try (var stack = stackPush()) {
            var bufferCreateInfo = VkBufferCreateInfo
                            .calloc(stack)
                            .sType$Default()
                            .size(size)
                            .usage(usage)
                            .pNext(VkExternalMemoryBufferCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .handleTypes(EXTERNAL_MEMORY_HANDLE_TYPE));

            var allocationCreateInfo = VmaAllocationCreateInfo.calloc(stack)
                            .requiredFlags(properties);
            
            var alloc = allocator.allocShared(bufferCreateInfo, allocationCreateInfo);

            int memoryObject = ExternalMemoryTracker.acquire(alloc, device, alloc.isDedicated());

            int glId = glCreateBuffers();
            glNamedBufferStorageMemEXT(glId, size, memoryObject, alloc.ai.offset());
            _CHECK_GL_ERROR_();
            return new VGBuffer(alloc, glId);
        }
    }

    public VGImage createSharedImage(int width, int height, int mipLevels, int vkFormat, int glFormat, int usage, int properties) {
        return createSharedImage(2, width, height, 1, mipLevels, vkFormat, glFormat, usage, properties);
    }
    public VGImage createSharedImage(int dimensions, int width, int height, int depth, int mipLevels, int vkFormat, int glFormat, int usage, int properties) {

        int vkImageType = VK_IMAGE_TYPE_2D;
        int glImageType = GL_TEXTURE_2D;

        if(dimensions == 1) {
            vkImageType = VK_IMAGE_TYPE_1D;
            glImageType = GL_TEXTURE_1D;
        } else if (dimensions == 3) {
            vkImageType = VK_IMAGE_TYPE_3D;
            glImageType = GL_TEXTURE_3D;
        }

        try (var stack = stackPush()) {
            var createInfo = VkImageCreateInfo
                    .calloc(stack)
                    .sType$Default()
                    .usage(usage)
                    .pNext(VkExternalMemoryImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .handleTypes(EXTERNAL_MEMORY_HANDLE_TYPE))
                    .format(vkFormat)
                    .imageType(vkImageType)
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(usage)
                    .samples(VK_SAMPLE_COUNT_1_BIT);
            createInfo.extent().width(width).height(height).depth(depth);

            var allocInfo = VmaAllocationCreateInfo.calloc(stack)
                            .requiredFlags(properties);

            var alloc = allocator.allocShared(createInfo, allocInfo);

            int memoryObject = ExternalMemoryTracker.acquire(alloc, device, alloc.isDedicated());

            int glId = glCreateTextures(glImageType);

            switch(glImageType) {
                case GL_TEXTURE_1D:
                    glTextureStorageMem1DEXT(glId, mipLevels, glFormat, width, memoryObject, alloc.ai.offset());
                    glTextureParameteri(glId, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTextureParameteri(glId, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTextureParameteri(glId, GL_TEXTURE_WRAP_S, GL_REPEAT);
                    break;
                case GL_TEXTURE_2D:
                    glTextureStorageMem2DEXT(glId, mipLevels, glFormat, width, height, memoryObject, alloc.ai.offset());
                    glTextureParameteri(glId, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTextureParameteri(glId, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTextureParameteri(glId, GL_TEXTURE_WRAP_S, GL_REPEAT);
                    glTextureParameteri(glId, GL_TEXTURE_WRAP_T, GL_REPEAT);
                    break;
                case GL_TEXTURE_3D:
                    glTextureStorageMem3DEXT(glId, mipLevels, glFormat, width, height, depth, memoryObject, alloc.ai.offset());
                    glTextureParameteri(glId, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTextureParameteri(glId, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTextureParameteri(glId, GL_TEXTURE_WRAP_S, GL_REPEAT);
                    glTextureParameteri(glId, GL_TEXTURE_WRAP_T, GL_REPEAT);
                    glTextureParameteri(glId, GL_TEXTURE_WRAP_R, GL_REPEAT);
                    break;
            }

            _CHECK_GL_ERROR_();
            return new VGImage(alloc, width, height, depth, mipLevels, vkFormat, glFormat, glId);
        }
    }

    public VBuffer createBuffer(long size, int usage, int properties) {
        return createBuffer(size, usage, properties, 0L, 0);
    }

    public VBuffer createBuffer(long size, int usage, int properties, long alignment, int vmaFlags) {
        try (var stack = stackPush()) {
            var alloc = allocator.alloc(0, VkBufferCreateInfo
                            .calloc(stack)
                            .sType$Default()
                            .size(size)
                            .usage(usage),
                    VmaAllocationCreateInfo.calloc(stack)
                            .usage(VMA_MEMORY_USAGE_AUTO)
                            .requiredFlags(properties),
                    alignment);
            return new VBuffer(alloc);
        }
    }

    public VImage createImage2D(int width, int height, int mipLevels, int vkFormat, int usage, int properties) {
        try (var stack = stackPush()) {
            var alloc = allocator.alloc(0, VkImageCreateInfo
                .calloc(stack)
                .sType$Default()
                .format(vkFormat)
                .imageType(VK_IMAGE_TYPE_2D)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .arrayLayers(1)
                .mipLevels(mipLevels)
                .extent(e -> e.width(width).height(height).depth(1))
                .usage(usage),
                    VmaAllocationCreateInfo.calloc(stack)
                            .usage(VMA_MEMORY_USAGE_AUTO)
                            .requiredFlags(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            return new VImage(alloc, width, height, 1, mipLevels, vkFormat);
        }
    }

    public VAccelerationStructure createAcceleration(long size, int alignment, int usage, int type) {
        var buffer = createBuffer(size, VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR | usage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, alignment, 0);
        try (var stack = stackPush()) {
            LongBuffer pAccelerationStructure = stack.mallocLong(1);
            _CHECK_(vkCreateAccelerationStructureKHR(device, VkAccelerationStructureCreateInfoKHR
                            .calloc(stack)
                            .sType$Default()
                            .type(type)
                            .size(size)
                            .buffer(buffer.buffer()), null, pAccelerationStructure),
                    "Failed to create acceleration acceleration structure");
            return new VAccelerationStructure(device, pAccelerationStructure.get(0), buffer);
        }
    }

    public void dumpStats() {
        System.out.println("VMA JSON:\n" + allocator.dumpJson(false));
    }
}
