package me.cortex.vulkanite.acceleration;

//TLAS manager, ingests blas build requests and manages builds and syncs the tlas

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.cmd.VCommandPool;
import me.cortex.vulkanite.lib.descriptors.DescriptorSetLayoutBuilder;
import me.cortex.vulkanite.lib.descriptors.DescriptorUpdateBuilder;
import me.cortex.vulkanite.lib.descriptors.VDescriptorPool;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.sync.VFence;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Pair;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Matrix4x3f;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class AccelerationTLASManager {
    private final EntityBlasBuilder entityBlasBuilder;
    private final TLASSectionManager buildDataManager = new TLASSectionManager();
    private final VContext context;
    private final int queue;
    private final VCommandPool singleUsePool;

    private final List<VAccelerationStructure> structuresToRelease = new ArrayList<>();

    private VAccelerationStructure currentTLAS;

    public AccelerationTLASManager(VContext context, int queue) {
        this.context = context;
        this.queue = queue;
        this.singleUsePool = context.cmd.createSingleUsePool();
        this.singleUsePool.setDebugUtilsObjectName("TLAS singleUsePool");
        this.buildDataManager.resizeBindlessSet(0, null);
        this.entityBlasBuilder = new EntityBlasBuilder(context);
    }

    // Returns a sync semaphore to chain in the next command submit
    public void updateSections(List<AccelerationBlasBuilder.BLASBuildResult> results) {
        for (var result : results) {

            // boolean canAcceptResult = (!result.section().isDisposed()) && result.time()
            // >= result.section().lastAcceptedBuildTime;

            buildDataManager.update(result);
        }
    }


    private List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> entityData;
    public void setEntityData(List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> data) {
        this.entityData = data;
    }


    public void removeSection(RenderSection section) {
        buildDataManager.remove(section);
    }

    // TODO: cleanup, this is very messy
    // FIXME: in the case of no geometry create an empty tlas or something???
    public void buildTLAS(VSemaphore semIn, VSemaphore semOut, VSemaphore[] blocking) {
        RenderSystem.assertOnRenderThread();

        singleUsePool.doReleases();

        if (buildDataManager.sectionCount() == 0) {
            if (blocking.length != 0) {
                // This case can happen when reloading or some other weird cases, only occurse
                // when the world _becomes_ empty for some reason, so just clear all the
                // semaphores
                // TODO: move to a destroy method or something in AccelerationManager instead of
                // here
                for (var semaphore : blocking) {
                    semaphore.free();
                }
            }
            return;
        }

        // NOTE: renderLink is required to ensure that we are not overriding memory that
        // is actively being used for frames
        // should have a VK_PIPELINE_STAGE_TRANSFER_BIT blocking bit
        try (var stack = stackPush()) {
            // The way the tlas build works is that terrain data is split up into regions,
            // each region is its own geometry input
            // this is done for performance reasons when updating (adding/removing) sections




            VkAccelerationStructureGeometryKHR geometry = VkAccelerationStructureGeometryKHR.calloc(stack);
            int instances = 0;

            var cmd = singleUsePool.createCommandBuffer();
            cmd.begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VFence fence = context.sync.createFence();

            Pair<VAccelerationStructure, VBuffer> entityBuild;
            if (entityData != null) {
                entityBuild = entityBlasBuilder.buildBlas(entityData, cmd, fence);
                context.sync.addCallback(fence, ()->{
                    entityBuild.getLeft().free();
                    entityBuild.getRight().free();
                });
            } else {
                entityBuild = null;
            }

            {
                // TODO: need to sync with respect to updates from gpu memory updates from
                // TLASBuildDataManager
                // OR SOMETHING CAUSE WITH MULTIPLE FRAMES GOING AT ONCE the gpu state of
                // TLASBuildDataManager needs to be synced with
                // the current build phase, and the gpu side needs to be updated accoringly and
                // synced correctly

                vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                        VkMemoryBarrier.calloc(1, stack)
                                .sType$Default()
                                .srcAccessMask(0)
                                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_TRANSFER_READ_BIT),
                        null, null);

                VkAccelerationStructureInstanceKHR extra = null;
                if (entityBuild != null) {
                    extra = VkAccelerationStructureInstanceKHR.calloc(stack);
                    extra.mask(~0)
                            .instanceCustomIndex(0)
                            .instanceShaderBindingTableRecordOffset(1)
                            .accelerationStructureReference(entityBuild.getLeft().deviceAddress);
                    extra.transform().matrix(new Matrix4x3f().getTransposed(stack.mallocFloat(12)));
                    buildDataManager.descUpdateJobs.add(new TLASSectionManager.DescUpdateJob(0,0, List.of(entityBuild.getRight())));
                    instances++;
                }
                buildDataManager.setGeometryUpdateMemory(fence, geometry, extra);
                instances += buildDataManager.sectionCount();

                vkCmdPipelineBarrier(cmd.buffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0,
                        VkMemoryBarrier.calloc(1, stack)
                                .sType$Default()
                                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT),
                        null, null);
            }



            int[] instanceCounts = new int[]{instances};
            {
                geometry.sType$Default()
                        .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR)
                        .flags(0);

                geometry.geometry()
                        .instances()
                        .sType$Default()
                        .arrayOfPointers(false);
            }


            // TLAS always rebuild & PREFER_FAST_TRACE according to Nvidia
            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                    .pGeometries(VkAccelerationStructureGeometryKHR.create(geometry.address(), 1))
                    .geometryCount(1);

            VkAccelerationStructureBuildSizesInfoKHR buildSizesInfo = VkAccelerationStructureBuildSizesInfoKHR
                    .calloc(stack)
                    .sType$Default();

            vkGetAccelerationStructureBuildSizesKHR(
                    context.device,
                    VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    buildInfo.get(0), // The reason its a buffer is cause of pain and that
                                      // vkCmdBuildAccelerationStructuresKHR requires a buffer of
                                      // VkAccelerationStructureBuildGeometryInfoKHR
                    stack.ints(instanceCounts),
                    buildSizesInfo);

            VAccelerationStructure tlas = context.memory.createAcceleration(buildSizesInfo.accelerationStructureSize(),
                    256,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR, VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR);

            // TODO: instead of making a new scratch buffer, try to reuse
            // ACTUALLY wait since we doing the on fence free thing, we dont have to worry
            // about that and it should
            // get automatically freed since we using vma dont have to worry about
            // performance _too_ much i think
            VBuffer scratchBuffer = context.memory.createBuffer(buildSizesInfo.buildScratchSize(),
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 256, 0);
            scratchBuffer.setDebugUtilsObjectName("TLAS Scratch Buffer");

            buildInfo.dstAccelerationStructure(tlas.structure)
                    .scratchData(VkDeviceOrHostAddressKHR.calloc(stack)
                            .deviceAddress(scratchBuffer.deviceAddress()));

            var buildRanges = VkAccelerationStructureBuildRangeInfoKHR.calloc(instanceCounts.length, stack);
            for (int count : instanceCounts) {
                buildRanges.get().primitiveCount(count);
            }
            buildRanges.rewind();

            vkCmdBuildAccelerationStructuresKHR(cmd.buffer,
                    buildInfo,
                    stack.pointers(buildRanges));

            cmd.end();

            int[] waitingStage = new int[blocking.length + 1];
            VSemaphore[] allBlocking = new VSemaphore[waitingStage.length];
            System.arraycopy(blocking, 0, allBlocking, 0, blocking.length);

            allBlocking[waitingStage.length - 1] = semIn;

            Arrays.fill(waitingStage,
                    VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR | VK_PIPELINE_STAGE_TRANSFER_BIT);
            context.cmd.submit(queue, new VCmdBuff[] { cmd }, allBlocking, waitingStage, new VSemaphore[] { semOut },
                    fence);

            VAccelerationStructure oldTLAS = currentTLAS;
            currentTLAS = tlas;

            List<VAccelerationStructure> capturedList = new ArrayList<>(structuresToRelease);
            structuresToRelease.clear();
            context.sync.addCallback(fence, () -> {
                scratchBuffer.free();
                if (oldTLAS != null) {
                    oldTLAS.free();
                }
                fence.free();
                cmd.enqueueFree();

                for (var as : capturedList) {
                    as.free();
                }

                // Release all the semaphores from the blas build system
                for (var sem : blocking) {
                    sem.free();
                }
            });
        }
    }

    public VAccelerationStructure getTlas() {
        return currentTLAS;
    }


    // Manages entries in the VkAccelerationStructureInstanceKHR buffer, ment to
    // reuse as much as possible and be very efficient
    private class TLASGeometryManager {
        // Have a global buffer for VkAccelerationStructureInstanceKHR, then use
        // VkAccelerationStructureGeometryInstancesDataKHR.arrayOfPointers
        // Use LibCString.memmove to ensure streaming data is compact
        // Stream this to the gpu per frame (not ideal tbh, could implement a cache of
        // some kind)

        // Needs a gpu buffer for the instance data, this can be reused
        // private VkAccelerationStructureInstanceKHR.Buffer buffer;

        private VkAccelerationStructureInstanceKHR.Buffer instances = VkAccelerationStructureInstanceKHR.calloc(30000);
        private int[] instance2pointer = new int[30000];
        private int[] pointer2instance = new int[30000];
        private BitSet free = new BitSet(30000);// The reason this is needed is to give non used instance ids
        private int count;

        public TLASGeometryManager() {
            free.set(0, instance2pointer.length);
        }

        // TODO: make the instances buffer, gpu permenent then stream updates instead of
        // uploading per frame
        public void setGeometryUpdateMemory(VFence fence, VkAccelerationStructureGeometryKHR struct, VkAccelerationStructureInstanceKHR addin) {
            long size = (long) VkAccelerationStructureInstanceKHR.SIZEOF * count;
            VBuffer data = context.memory.createBuffer(size + (addin==null?0:VkAccelerationStructureInstanceKHR.SIZEOF),
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT
                            | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR
                            | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VK_MEMORY_HEAP_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    0, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
            data.setDebugUtilsObjectName("TLAS Instance Buffer");
            long ptr = data.map();
            if (addin != null) {
                MemoryUtil.memCopy(addin.address(), ptr, VkAccelerationStructureInstanceKHR.SIZEOF);
                ptr += VkAccelerationStructureInstanceKHR.SIZEOF;
            }
            MemoryUtil.memCopy(this.instances.address(0), ptr, size);

            data.unmap();
            data.flush();

            struct.geometry()
                    .instances()
                    .data()
                    .deviceAddress(data.deviceAddress());

            context.sync.addCallback(fence, () -> {
                data.free();
            });
        }

        public int sectionCount() {
            return count;
        }

        protected int alloc() {
            int id = free.nextSetBit(0);

            free.clear(id);

            // Update the map
            instance2pointer[id] = count;
            pointer2instance[count] = id;

            // Increment the count
            count++;

            return id;
        }

        protected void free(int id) {
            free.set(id);

            count--;
            if (instance2pointer[id] == count) {
                // We are at the end of the pointer list, so just decrement and be done
                instance2pointer[id] = -1;
                pointer2instance[count] = -1;
            } else {
                // TODO: CHECK THIS IS CORRECT

                // We need to remove the pointer, and fill it in with the last element in the
                // pointer array, updating the mapping of the moved
                int ptrId = instance2pointer[id];
                instance2pointer[id] = -1;

                // I feel like this should be pointer2instance = pointer2instance
                pointer2instance[ptrId] = pointer2instance[count];

                // move over the ending data to the missing hole point
                MemoryUtil.memCopy(instances.address(count), instances.address(ptrId),
                        VkAccelerationStructureInstanceKHR.SIZEOF);

                instance2pointer[pointer2instance[count]] = ptrId;
            }
        }

        protected void update(int id, VkAccelerationStructureInstanceKHR data) {
            MemoryUtil.memCopy(data.address(), instances.address(instance2pointer[id]),
                    VkAccelerationStructureInstanceKHR.SIZEOF);
        }
    }

    private static int roundUpPow2(int v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    private final class TLASSectionManager extends TLASGeometryManager {
        private final TlasPointerArena arena = new TlasPointerArena(30000);

        public TLASSectionManager() {
            //Allocate index 0 to entity blas
            if (arena.allocate(1) != 0) {
                throw new IllegalStateException();
            }
        }

        private VDescriptorSetLayout geometryBufferSetLayout;
        private VDescriptorPool geometryBufferDescPool;
        private long geometryBufferDescSet = 0;

        private int setCapacity = 0;

        private record DescUpdateJob(int binding, int dstArrayElement, List<VBuffer> buffers) {
        }

        private record ArenaDeallocJob(int index, int count, List<VBuffer> geometryBuffers) {
        }

        private final ConcurrentLinkedDeque<DescUpdateJob> descUpdateJobs = new ConcurrentLinkedDeque<>();
        private final ConcurrentLinkedDeque<ArenaDeallocJob> arenaDeallocJobs = new ConcurrentLinkedDeque<>();
        private final Deque<VDescriptorPool> descPoolsToRelease = new ArrayDeque<>();

        public void resizeBindlessSet(int newSize, VFence fence) {
            if (geometryBufferSetLayout == null) {
                var layoutBuilder = new DescriptorSetLayoutBuilder(
                        VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT);
                layoutBuilder.binding(0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 65536, VK_SHADER_STAGE_ALL);
                layoutBuilder.setBindingFlags(0,
                        VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                | VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT
                                | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT);
                geometryBufferSetLayout = layoutBuilder.build(context);
            }

            if (newSize > setCapacity) {
                int newCapacity = roundUpPow2(Math.max(newSize, 32));
                var newGeometryBufferDescPool = new VDescriptorPool(context,
                        VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT, 1, newCapacity, geometryBufferSetLayout.types);
                newGeometryBufferDescPool.allocateSets(geometryBufferSetLayout, new int[] { newCapacity });
                long newGeometryBufferDescSet = newGeometryBufferDescPool.get(0);

                System.out.println("New geometry desc set: " + Long.toHexString(newGeometryBufferDescSet)
                        + " with capacity " + newCapacity);

                if (geometryBufferDescSet != 0) {
                    try (var stack = stackPush()) {
                        var setCopy = VkCopyDescriptorSet.calloc(1, stack);
                        setCopy.get(0)
                                .sType$Default()
                                .srcSet(geometryBufferDescSet)
                                .dstSet(newGeometryBufferDescSet)
                                .descriptorCount(setCapacity);
                        vkUpdateDescriptorSets(context.device, null, setCopy);
                    }

                    descPoolsToRelease.add(geometryBufferDescPool);
                }

                geometryBufferDescPool = newGeometryBufferDescPool;
                geometryBufferDescSet = newGeometryBufferDescSet;
                setCapacity = newCapacity;
            }

        }

        @Override
        public void setGeometryUpdateMemory(VFence fence, VkAccelerationStructureGeometryKHR struct, VkAccelerationStructureInstanceKHR addin) {
            super.setGeometryUpdateMemory(fence, struct, addin);
            resizeBindlessSet(arena.maxIndex, fence);

            if (descUpdateJobs.isEmpty()) {
                return;
            }

            var dub = new DescriptorUpdateBuilder(context, descUpdateJobs.size());
            dub.set(geometryBufferDescSet);
            while (!descUpdateJobs.isEmpty()) {
                var job = descUpdateJobs.poll();
                dub.buffer(job.binding, job.dstArrayElement, job.buffers);
            }
            dub.apply();

            // Queue up the arena dealloc jobs to be done after the fence is done
            Vulkanite.INSTANCE.addSyncedCallback(() -> {
                fenceTick();
            });
        }

        // TODO: mixinto RenderSection and add a reference to a holder for us, its much
        // faster than a hashmap
        private static final class Holder {
            final int id;
            int geometryIndex = -1;
            List<VBuffer> geometryBuffers;

            final RenderSection section;
            VAccelerationStructure structure;

            private Holder(int id, RenderSection section) {
                this.id = id;
                this.section = section;
            }
        }

        Map<ChunkSectionPos, Holder> tmp = new HashMap<>();

        public void fenceTick() {
            while (!arenaDeallocJobs.isEmpty()) {
                var job = arenaDeallocJobs.poll();
                arena.free(job.index, job.count);
                job.geometryBuffers.forEach(buffer -> buffer.free());
            }
            while (!descPoolsToRelease.isEmpty()) {
                descPoolsToRelease.poll().free();
            }
        }

        public void update(AccelerationBlasBuilder.BLASBuildResult result) {
            var data = result.data();
            var holder = tmp.computeIfAbsent(data.section().getPosition(), a -> new Holder(alloc(), data.section()));
            if (holder.structure != null) {
                structuresToRelease.add(holder.structure);
            }
            holder.structure = result.structure();

            if (holder.geometryIndex != -1) {
                arenaDeallocJobs.add(new ArenaDeallocJob(holder.geometryIndex, holder.geometryBuffers.size(),
                        holder.geometryBuffers));
            }
            holder.geometryBuffers = data.geometryBuffers();
            holder.geometryIndex = arena.allocate(holder.geometryBuffers.size());

            descUpdateJobs.add(new DescUpdateJob(0, holder.geometryIndex, holder.geometryBuffers));

            try (var stack = stackPush()) {
                var asi = VkAccelerationStructureInstanceKHR.calloc(stack)
                        .mask(~0)
                        .instanceCustomIndex(holder.geometryIndex)
                        .accelerationStructureReference(holder.structure.deviceAddress);
                asi.transform()
                        .matrix(new Matrix4x3f()
                                .translate(holder.section.getOriginX(), holder.section.getOriginY(),
                                        holder.section.getOriginZ())
                                .getTransposed(stack.mallocFloat(12)));
                update(holder.id, asi);
            }
        }

        public void remove(RenderSection section) {
            var holder = tmp.remove(section.getPosition());
            if (holder == null)
                return;

            structuresToRelease.add(holder.structure);

            free(holder.id);

            for (var job : descUpdateJobs) {
                if (job.buffers == holder.geometryBuffers) {
                    descUpdateJobs.remove(job);
                }
            }

            if (holder.geometryIndex != -1) {
                arenaDeallocJobs.add(new ArenaDeallocJob(holder.geometryIndex, holder.geometryBuffers.size(),
                        holder.geometryBuffers));
            }
        }
    }

    private static final class TlasPointerArena {
        private final BitSet vacant;
        public int maxIndex = 0;

        private TlasPointerArena(int size) {
            size *= 3;
            vacant = new BitSet(size);
            vacant.set(0, size);
        }

        public int allocate(int count) {
            int pos = vacant.nextSetBit(0);
            outer: while (pos != -1) {
                for (int offset = 1; offset < count; offset++) {
                    if (!vacant.get(offset + pos)) {
                        pos = vacant.nextSetBit(offset + pos + 1);
                        continue outer;
                    }
                }
                break;
            }
            if (pos == -1) {
                throw new IllegalStateException();
            }
            vacant.clear(pos, pos + count);
            maxIndex = Math.max(maxIndex, pos + count);
            return pos;
        }

        public void free(int pos, int count) {
            vacant.set(pos, pos + count);

            maxIndex = vacant.previousClearBit(maxIndex) + 1;
        }
    }

    public long getGeometrySet() {
        return buildDataManager.geometryBufferDescSet;
    }

    public VDescriptorSetLayout getGeometryLayout() {
        return buildDataManager.geometryBufferSetLayout;
    }

    // Called for cleaning up any remaining loose resources
    void cleanupTick() {
        singleUsePool.doReleases();
        structuresToRelease.forEach(VAccelerationStructure::free);
        structuresToRelease.clear();
        if (currentTLAS != null) {
            currentTLAS.free();
            currentTLAS = null;
        }
        if (buildDataManager.sectionCount() != 0) {
            throw new IllegalStateException("Sections are not empty on cleanup");
        }
    }
}
