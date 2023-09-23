package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AccelerationManager {
    private final VContext ctx;

    private final AccelerationBlasBuilder blasBuilder;
    private final ConcurrentLinkedDeque<AccelerationBlasBuilder.BLASBatchResult> blasResults = new ConcurrentLinkedDeque<>();

    private final AccelerationTLASManager tlasManager;

    public AccelerationManager(VContext context, int blasBuildQueue) {
        this.ctx = context;
        this.blasBuilder = new AccelerationBlasBuilder(context, blasBuildQueue, blasResults::add);
        this.tlasManager = new AccelerationTLASManager(context, 0);//TODO: pick the main queue or something? (maybe can do the blasBuildQueue)
    }

    public void chunkBuilds(List<ChunkBuildOutput> results) {
        blasBuilder.enqueue(results);
    }

    private final List<VSemaphore> syncs = new LinkedList<>();

    //This updates the tlas internal structure, DOES NOT INCLUDING BUILDING THE TLAS
    public void updateTick() {
        if (!blasResults.isEmpty()) {//If there are results
            //Atomicly collect the results from the queue
            List<AccelerationBlasBuilder.BLASBuildResult> results = new LinkedList<>();
            while (!blasResults.isEmpty()) {
                var batch = blasResults.poll();
                results.addAll(batch.results());
                syncs.add(batch.semaphore());
            }
            tlasManager.updateSections(results);
        }
    }

    public VAccelerationStructure buildTLAS(VSemaphore inLink, VSemaphore outLink) {
        tlasManager.buildTLAS(inLink, outLink, syncs.toArray(new VSemaphore[0]));
        syncs.clear();
        return tlasManager.getTlas();
    }

    public void sectionRemove(RenderSection section) {
        tlasManager.removeSection(section);
    }

    //Cleans up any loose things such as semaphores waiting to be synced etc
    public void cleanup() {
        //TODO: FIXME: I DONT THINK THIS IS CORRECT OR WORKS, IM STILL LEAKING VRAM MEMORY OUT THE WAZOO WHEN f3+a reloading
        ctx.cmd.waitQueueIdle(0);
        ctx.cmd.waitQueueIdle(1);
        try {
            Thread.sleep(250L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ctx.cmd.waitQueueIdle(0);
        ctx.cmd.waitQueueIdle(1);
        syncs.forEach(VSemaphore::free);
        syncs.clear();
        tlasManager.cleanupTick();
    }

    public long getGeometrySet() {
        return tlasManager.getGeometrySet();
    }

    public VDescriptorSetLayout getGeometryLayout() {
        return tlasManager.getGeometryLayout();
    }
}
