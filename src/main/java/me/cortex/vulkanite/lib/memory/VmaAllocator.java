package me.cortex.vulkanite.lib.memory;

import me.cortex.vulkanite.lib.base.TrackedResourceObject;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;
import org.lwjgl.util.vma.*;
import org.lwjgl.vulkan.*;

import java.lang.ref.Cleaner;
import java.nio.LongBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.vkGetBufferDeviceAddressKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

//TODO: make multiple pools for allocations
public class VmaAllocator {
    private final VkDevice device;
    private final long allocator;
    private final boolean hasDeviceAddresses;
    public VmaAllocator(VkDevice device, boolean enableDeviceAddresses) {
        this.device = device;
        this.hasDeviceAddresses = enableDeviceAddresses;
        try (var stack = stackPush()){
            VmaAllocatorCreateInfo allocatorCreateInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(device.getPhysicalDevice().getInstance())
                    .physicalDevice(device.getPhysicalDevice())
                    .device(device)
                    .pVulkanFunctions(VmaVulkanFunctions
                            .calloc(stack)
                            .set(device.getPhysicalDevice().getInstance(), device))
                    .flags(enableDeviceAddresses?VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT:0);

            /*
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(device.device.getPhysicalDevice(), memoryProperties);

            IntBuffer handleTypes = MemoryUtil.memAllocInt(memoryProperties.memoryTypeCount());
            for (int i = 0; i < handleTypes.capacity(); i++) {
                handleTypes.put(i, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            }
            allocatorCreateInfo.pTypeExternalMemoryHandleTypes(handleTypes);
             */


            PointerBuffer pAllocator = stack.pointers(0);
            if (vmaCreateAllocator(allocatorCreateInfo, pAllocator) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create allocator");
            }

            allocator = pAllocator.get(0);
        }
    }

    public MemoryPool createPool() {
        return new MemoryPool(0);
    }

    public MemoryPool createPool(Struct chain) {
        return new MemoryPool(chain.address());
    }


    //TODO: FIXME: find a better way to synchronize this, since it needs to be very fast
    private static final Lock ALLOCATOR_LOCK = new ReentrantLock();

    //NOTE: SHOULD ONLY BE USED TO ALLOCATE SHARED MEMORY AND STUFF, not recommended
    BufferAllocation alloc(long pool, VkBufferCreateInfo bufferCreateInfo, VmaAllocationCreateInfo allocationCreateInfo) {
        try (var stack = stackPush()) {
            LongBuffer pb = stack.mallocLong(1);
            PointerBuffer pa = stack.mallocPointer(1);
            VmaAllocationInfo vai = VmaAllocationInfo.calloc();
            ALLOCATOR_LOCK.lock();
            try {
                _CHECK_(
                        vmaCreateBuffer(allocator,
                                bufferCreateInfo,
                                allocationCreateInfo.pool(pool),
                                pb,
                                pa,
                                vai),
                        "Failed to allocate buffer");
            } finally {
                ALLOCATOR_LOCK.unlock();
            }
            return new BufferAllocation(pb.get(0), pa.get(0), vai, (bufferCreateInfo.usage()&VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT) != 0);
        }
    }

    BufferAllocation alloc(long pool, VkBufferCreateInfo bufferCreateInfo, VmaAllocationCreateInfo allocationCreateInfo, long alignment) {
        if (alignment == 0) return alloc(pool, bufferCreateInfo, allocationCreateInfo);
        try (var stack = stackPush()) {
            LongBuffer pb = stack.mallocLong(1);
            PointerBuffer pa = stack.mallocPointer(1);
            VmaAllocationInfo vai = VmaAllocationInfo.calloc();
            ALLOCATOR_LOCK.lock();
            try {
                _CHECK_(
                        vmaCreateBufferWithAlignment(allocator,
                                bufferCreateInfo,
                                allocationCreateInfo.pool(pool),
                                alignment,
                                pb,
                                pa,
                                vai),
                        "Failed to allocate buffer");
            } finally {
                ALLOCATOR_LOCK.unlock();
            }
            return new BufferAllocation(pb.get(0), pa.get(0), vai, (bufferCreateInfo.usage()&VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT) != 0);
        }
    }

    ImageAllocation alloc(long pool, VkImageCreateInfo bufferCreateInfo, VmaAllocationCreateInfo allocationCreateInfo) {
        try (var stack = stackPush()) {
            LongBuffer pi = stack.mallocLong(1);
            PointerBuffer pa = stack.mallocPointer(1);
            VmaAllocationInfo vai = VmaAllocationInfo.calloc();
            _CHECK_(
                    vmaCreateImage(allocator,
                            bufferCreateInfo,
                            allocationCreateInfo.pool(pool),
                            pi,
                            pa,
                            vai),
                    "Failed to allocate buffer");
            return new ImageAllocation(pi.get(0), pa.get(0), vai);
        }
    }


    public abstract static class Allocation extends TrackedResourceObject {
        public final VmaAllocationInfo ai;
        public final long allocation;

        public Allocation(long allocation, VmaAllocationInfo info) {

            this.ai = info;
            this.allocation = allocation;
        }

        public void free() {
            free0();
            //vmaFreeMemory(allocator, allocation);
            ai.free();
        }
    }

    public class BufferAllocation extends Allocation {
        public final long buffer;
        public final long deviceAddress;
        public BufferAllocation(long buffer, long allocation, VmaAllocationInfo info, boolean hasDeviceAddress) {
            super(allocation, info);
            this.buffer = buffer;
            if (hasDeviceAddresses && hasDeviceAddress) {
                try (MemoryStack stack = stackPush()) {
                    deviceAddress = vkGetBufferDeviceAddressKHR(device, VkBufferDeviceAddressInfo
                            .calloc(stack)
                            .sType$Default()
                            .buffer(buffer));
                }
            } else {
                deviceAddress = -1;
            }
        }

        @Override
        public void free() {
            //vkFreeMemory();
            ALLOCATOR_LOCK.lock();
            try {
                vmaDestroyBuffer(allocator, buffer, allocation);
                super.free();
            } finally {
                ALLOCATOR_LOCK.unlock();
            }
        }

        public VkDevice getDevice() {
            return device;
        }

        //TODO: Maybe put the following 3 in VBuffer
        public long map() {
            ALLOCATOR_LOCK.lock();
            try {
                try(var stack = stackPush()) {
                    PointerBuffer res = stack.callocPointer(1);
                        _CHECK_(vmaMapMemory(allocator, allocation, res));
                    return res.get(0);
                }
            } finally {
                ALLOCATOR_LOCK.unlock();
            }
        }

        public void unmap() {
            ALLOCATOR_LOCK.lock();
            try {
                vmaUnmapMemory(allocator, allocation);
            } finally {
                ALLOCATOR_LOCK.unlock();
            }
        }

        public void flush(long offset, long size) {
            //TODO: offset must be a multiple of VkPhysicalDeviceLimits::nonCoherentAtomSize
            try (var stack = stackPush()) {
                /*
                _CHECK_(vkFlushMappedMemoryRanges(device, VkMappedMemoryRange
                        .calloc(stack)
                        .sType$Default()
                        .memory(ai.deviceMemory())
                        .size(size)
                        .offset(ai.offset()+offset)));*/
                ALLOCATOR_LOCK.lock();
                try {
                    vmaFlushAllocation(allocator, allocation, offset, size);
                } finally {
                    ALLOCATOR_LOCK.unlock();
                }
            }
        }
    }

    public class ImageAllocation extends Allocation {
        public final long image;
        public ImageAllocation(long image, long allocation, VmaAllocationInfo info) {
            super(allocation, info);
            this.image = image;
        }

        @Override
        public void free() {
            //vkFreeMemory();
            ALLOCATOR_LOCK.lock();
            try {
                vmaDestroyImage(allocator, image, allocation);
                super.free();
            } finally {
                ALLOCATOR_LOCK.unlock();
            }
        }
    }

    public class MemoryPool {
        private final long pool;
        public MemoryPool(long pNext) {
            try (var stack = stackPush()) {
                VmaPoolCreateInfo pci = VmaPoolCreateInfo.calloc(stack);
                pci.pMemoryAllocateNext(pNext);
                PointerBuffer pb = stack.callocPointer(1);
                if (vmaCreatePool(allocator, pci, pb) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create memory pool");
                }
                pool = pb.get(0);
            }
        }
        BufferAllocation alloc(VkBufferCreateInfo bufferCreateInfo, VmaAllocationCreateInfo allocationCreateInfo) {
            return VmaAllocator.this.alloc(pool, bufferCreateInfo, allocationCreateInfo);
        }

        BufferAllocation alloc(VkBufferCreateInfo bufferCreateInfo, VmaAllocationCreateInfo allocationCreateInfo, long alignment) {
            return VmaAllocator.this.alloc(pool, bufferCreateInfo, allocationCreateInfo, alignment);
        }
        public ImageAllocation alloc(VkImageCreateInfo bufferCreateInfo, VmaAllocationCreateInfo allocationCreateInfo) {
            return VmaAllocator.this.alloc(pool, bufferCreateInfo, allocationCreateInfo);
        }
    }


    public String dumpJson(boolean detailed) {
        try (var stack = stackPush()) {
            PointerBuffer pb = stack.callocPointer(1);
            vmaBuildStatsString(allocator, pb, detailed);
            String result = MemoryUtil.memUTF8(pb.get(0));
            nvmaFreeStatsString(allocator, pb.get(0));
            return result;
        }
    }
}
