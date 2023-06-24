package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.lib.base.VContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AccelerationManager {
    private final VContext ctx;

    private final AccelerationBlasBuilder blasBuilder;
    private final ConcurrentLinkedDeque<AccelerationBlasBuilder.BLASBatchResult> blasResults = new ConcurrentLinkedDeque<>();

    public AccelerationManager(VContext context, int blasBuildQueue) {
        this.ctx = context;
        this.blasBuilder = new AccelerationBlasBuilder(context, blasBuildQueue, blasResults::add);
    }

    public void chunkBuild(ChunkBuildResult result) {

    }

    //This is the primary render thread tick, called once per frame updates the tlas
    public void updateTick() {
        if (!blasResults.isEmpty()) {//If there are results
            //Atomicly collect the results from the queue
            LinkedList<AccelerationBlasBuilder.BLASBatchResult> results = new LinkedList<>();
            while (!blasResults.isEmpty()) results.add(blasResults.poll());


        }
    }
}
