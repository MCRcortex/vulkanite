package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.client.IAccelerationBuildResult;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;

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
        this.tlasManager = new AccelerationTLASManager(context);
    }

    public void chunkBuild(ChunkBuildResult result) {
        var accelerationData = ((IAccelerationBuildResult)result).getAccelerationGeometryData();

        //TODO: instead of submitting them one at a time, batch them
        blasBuilder.enqueue();

        accelerationData.values().forEach(NativeBuffer::free);
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

            var resultSemaphore = tlasManager.updateTLAS(syncs, results);
        }
    }
}
