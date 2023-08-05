package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;

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

    public void chunkBuilds(List<ChunkBuildResult> results) {
        blasBuilder.enqueue(results);
    }

    //This is the primary render thread tick, called once per frame updates the tlas
    public void updateTick() {
        if (!blasResults.isEmpty()) {//If there are results
            //Atomicly collect the results from the queue
            List<AccelerationBlasBuilder.BLASBuildResult> results = new LinkedList<>();
            List<VSemaphore> syncs = new LinkedList<>();
            while (!blasResults.isEmpty()) {
                var batch = blasResults.poll();
                results.addAll(batch.results());
                syncs.add(batch.semaphore());
            }
            tlasManager.updateSections(results);
            //var resultSemaphore = tlasManager.buildTLAS(syncs, results);
        }
        //tlasManager.buildTLAS(null, new VSemaphore[0]);
    }

    public void sectionRemove(RenderSection section) {
        tlasManager.removeSection(section);
    }
}
