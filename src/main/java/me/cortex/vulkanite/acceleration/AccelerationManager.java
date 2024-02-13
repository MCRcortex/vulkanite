package me.cortex.vulkanite.acceleration;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.cmd.VCmdBuff;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSet;
import me.cortex.vulkanite.lib.descriptors.VDescriptorSetLayout;
import me.cortex.vulkanite.lib.memory.VAccelerationStructure;
import me.cortex.vulkanite.lib.memory.VBuffer;
import me.cortex.vulkanite.lib.other.sync.VSemaphore;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Pair;

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


    public void setEntityData(List<Pair<RenderLayer, BufferBuilder.BuiltBuffer>> data) {
        tlasManager.setEntityData(data);
    }

    private final List<Long> blasExecutions = new LinkedList<>();

    //This updates the tlas internal structure, DOES NOT INCLUDING BUILDING THE TLAS
    public void updateTick() {
        if (!blasResults.isEmpty()) {//If there are results
            //Atomicly collect the results from the queue
            List<AccelerationBlasBuilder.BLASBuildResult> results = new LinkedList<>();
            while (!blasResults.isEmpty()) {
                var batch = blasResults.poll();
                results.addAll(batch.results());
                blasExecutions.add(batch.execution());
            }
            tlasManager.updateSections(results);
        }
    }

    public VRef<VAccelerationStructure> buildTLAS(int queueId, VCmdBuff cmd) {
        blasExecutions.forEach(exec -> ctx.cmd.queueWaitForExeuction(queueId, blasBuilder.getAsyncQueue(), exec));
        blasExecutions.clear();
        tlasManager.buildTLAS(cmd);
        return tlasManager.getTlas();
    }

    public void sectionRemove(RenderSection section) {
        tlasManager.removeSection(section);
    }

    public VRef<VDescriptorSet> getGeometrySet() {
        return tlasManager.getGeometrySet();
    }

    public VRef<VDescriptorSetLayout> getGeometryLayout() {
        return tlasManager.getGeometryLayout();
    }
}
