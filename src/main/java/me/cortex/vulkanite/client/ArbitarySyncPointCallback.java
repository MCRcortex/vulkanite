package me.cortex.vulkanite.client;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import org.lwjgl.opengl.GL32C;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.opengl.ARBSync.*;
import static org.lwjgl.opengl.GL11C.glGetError;

public class ArbitarySyncPointCallback {
    private final List<Runnable> callbacks = new ArrayList<>();
    private final Lock lock = new ReentrantLock();
    public void enqueue(Runnable callback) {
        lock.lock();
        callbacks.add(callback);
        lock.unlock();
    }

    public Runnable generateCallback() {
        //Capture the callbacks into a local array
        lock.lock();
        var capturedCallbacks = new ArrayList<>(callbacks);
        callbacks.clear();
        lock.unlock();
        if (capturedCallbacks.size() == 0) {
            return null;
        } else {
            return () -> capturedCallbacks.forEach(Runnable::run);
        }
    }


    private final LongArrayFIFOQueue fenceQueue = new LongArrayFIFOQueue();
    private final ObjectArrayFIFOQueue<Runnable> callbackQueue = new ObjectArrayFIFOQueue<>();
    //Injects a glFence into the cmd stream if there are any callbacks that need to be executed
    public void tick() {
        var callback = generateCallback();
        if (callback != null) {
            long fence = GL32C.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence == 0) {
                throw new IllegalStateException("glFenceSync was 0");
            }
            fenceQueue.enqueue(fence);
            callbackQueue.enqueue(callback);
        }

        while (!fenceQueue.isEmpty()) {
            int result = glClientWaitSync(fenceQueue.firstLong(), 0, 1);
            if (result == GL_ALREADY_SIGNALED || result == GL_CONDITION_SATISFIED) {
                glDeleteSync(fenceQueue.dequeueLong());
                callbackQueue.dequeue().run();
            } else if (result == GL_WAIT_FAILED ) {
                throw new IllegalStateException("Other exception occurred waiting polling sync object: " + glGetError());
            } else {
                break;
            }
        }
    }
}
