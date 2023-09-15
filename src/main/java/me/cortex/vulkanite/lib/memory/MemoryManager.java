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

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static me.cortex.vulkanite.lib.other.VUtil._CHECK_GL_ERROR_;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.EXTMemoryObject.*;
import static org.lwjgl.opengl.EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT;
import static org.lwjgl.opengl.EXTMemoryObjectFD.glImportMemoryFdEXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkCreateAccelerationStructureKHR;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.vkGetMemoryFdKHR;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class MemoryManager {
    private static final int EXTERNAL_MEMORY_HANDLE_TYPE = Vulkanite.IS_WINDOWS?VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT:VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
    private final VkDevice device;
    private final VmaAllocator allocator;
    private final VmaAllocator.MemoryPool shared;
    private final boolean hasDeviceAddresses;
    //VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT_KHR
    //TODO: FIXME: i think the vma allocator must be allocated with VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT, or at least the pools should
    public MemoryManager(VkDevice device, boolean hasDeviceAddresses) {
        this.device = device;
        this.hasDeviceAddresses = hasDeviceAddresses;
        allocator = new VmaAllocator(device, hasDeviceAddresses);
        //Note: this technically creates a memory leak, since we never free it, however
        // memory manager should never be created more than once per application, so it should bo ok
        shared = allocator.createPool(VkExportMemoryAllocateInfo.calloc()
                .sType$Default()
                .handleTypes(EXTERNAL_MEMORY_HANDLE_TYPE));
    }

    private long importMemoryWin32(int memoryObject, VmaAllocator.Allocation allocation) {
        try (var stack = stackPush()) {
            PointerBuffer pb = stack.callocPointer(1);
            _CHECK_(vkGetMemoryWin32HandleKHR(device, VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(allocation.ai.deviceMemory())
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT), pb));
            long handle = pb.get(0);
            if (handle == 0)
                throw new IllegalStateException();
            HandleDescriptorManger.add(handle);

            //TODO: fixme: the `alloc.ai.size() + alloc.ai.offset()` is an extreamly ugly hack
            // it is ment to extend over the entire size of vkMemoryObject, but im not sure how to obtain it
            glImportMemoryWin32HandleEXT(memoryObject, allocation.ai.size() + allocation.ai.offset(), GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
            return handle;
        }
    }

    private int importMemoryFd(int memoryObject, VmaAllocator.Allocation allocation) {
        try (var stack = stackPush()) {
            IntBuffer pb = stack.callocInt(1);
            _CHECK_(vkGetMemoryFdKHR(device, VkMemoryGetFdInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(allocation.ai.deviceMemory())
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT), pb));
            int descriptor = pb.get(0);
            if (descriptor == 0)
                throw new IllegalStateException();
            HandleDescriptorManger.add(descriptor);

            //TODO: fixme: the `alloc.ai.size() + alloc.ai.offset()` is an extreamly ugly hack
            // it is ment to extend over the entire size of vkMemoryObject, but im not sure how to obtain it
            glImportMemoryFdEXT(memoryObject, allocation.ai.size() + allocation.ai.offset(), GL_HANDLE_TYPE_OPAQUE_FD_EXT, descriptor);
            return descriptor;
        }
    }
    private long importMemory(int memoryObject, VmaAllocator.Allocation allocation) {
        return Vulkanite.IS_WINDOWS?importMemoryWin32(memoryObject,allocation):importMemoryFd(memoryObject,allocation);
    }

    //TODO: there is a better way to do shared memory, a vk memory object from vma should be put into a single memory object
    // then the memory object should be reused multiple times, this is the corrent and more efficent way
    // that is, since `alloc.ai.deviceMemory()` is shared by multiple allocations, they can also share a single memory object
    public VGBuffer createSharedBuffer(long size, int usage, int properties) {
        return createSharedBuffer(size, usage, properties, 0);
    }
    public VGBuffer createSharedBuffer(long size, int usage, int properties, int alignment) {
        try (var stack = stackPush()) {
            var alloc = shared.alloc(VkBufferCreateInfo
                            .calloc(stack)
                            .sType$Default()
                            .size(size)
                            .usage(usage)
                            .pNext(VkExternalMemoryBufferCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .handleTypes(EXTERNAL_MEMORY_HANDLE_TYPE)),
                    // VERY IMPORTANT: create dedicated allocation so underlying memory object is only used by this buffer,
                    // thus only imported once.
                    VmaAllocationCreateInfo.calloc(stack)
                            .usage(VMA_MEMORY_USAGE_AUTO)
                            .requiredFlags(properties)
                    ,//.flags(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT)
                    alignment);

            int memoryObject = glCreateMemoryObjectsEXT();
            long handle = importMemory(memoryObject, alloc);

            int glId = glCreateBuffers();
            glNamedBufferStorageMemEXT(glId, size, memoryObject, alloc.ai.offset());
            _CHECK_GL_ERROR_();
            return new VGBuffer(alloc, glId, memoryObject, handle);
        }
    }

    public VGImage createSharedImage(int width, int height, int mipLevels, int vkFormat, int glFormat, int usage, int properties) {
        try (var stack = stackPush()) {
            var createInfo = VkImageCreateInfo
                    .calloc(stack)
                    .sType$Default()
                    .usage(usage)
                    .pNext(VkExternalMemoryImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .handleTypes(EXTERNAL_MEMORY_HANDLE_TYPE))
                    .format(vkFormat)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(usage)
                    .samples(VK_SAMPLE_COUNT_1_BIT);
            createInfo.extent().width(width).height(height).depth(1);
            var alloc = shared.alloc(createInfo,
                    VmaAllocationCreateInfo.calloc(stack)
                            .usage(VMA_MEMORY_USAGE_AUTO)
                            .requiredFlags(properties)
                            //.flags(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT)
            );

            int memoryObject = glCreateMemoryObjectsEXT();
            long handle = importMemory(memoryObject, alloc);

            int glId = glCreateTextures(GL_TEXTURE_2D);

            glTextureStorageMem2DEXT(glId, mipLevels, glFormat, width, height, memoryObject, alloc.ai.offset());
            glTextureParameteri(glId, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTextureParameteri(glId, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTextureParameteri(glId, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTextureParameteri(glId, GL_TEXTURE_WRAP_T, GL_REPEAT);

            _CHECK_GL_ERROR_();
            return new VGImage(alloc, width, height, mipLevels, vkFormat, glFormat, glId, memoryObject, handle);
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

    public VImage creatImage2D(int width, int height, int mipLevels, int vkFormat, int usage, int properties) {
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
            return new VImage(alloc, width, height, mipLevels, vkFormat);
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
}
