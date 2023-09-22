package me.cortex.vulkanite.lib.memory;

import com.sun.jna.Pointer;
import com.sun.jna.platform.linux.LibC;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.vulkanite.client.Vulkanite;

public class HandleDescriptorManger {
    private static final Long2IntOpenHashMap USED_HANDLE_DESCRIPTORS = new Long2IntOpenHashMap();
    public static void add(long handleDescriptor) {
        synchronized (USED_HANDLE_DESCRIPTORS) {
            USED_HANDLE_DESCRIPTORS.addTo(handleDescriptor, 1);
        }
    }

    public static void close(long handleDescriptor) {
        synchronized (USED_HANDLE_DESCRIPTORS) {
            int val = USED_HANDLE_DESCRIPTORS.addTo(handleDescriptor, -1);
            if (val <= 0) {
                throw new IllegalStateException();
            }
            if (val == 1) {
                USED_HANDLE_DESCRIPTORS.remove(handleDescriptor);
                if (Vulkanite.IS_WINDOWS) {
                    if (!Kernel32.INSTANCE.CloseHandle(new WinNT.HANDLE(new Pointer(handleDescriptor)))) {
                        throw new IllegalStateException();
                    }
                } else {
                    if (LibC.INSTANCE.close((int) handleDescriptor) != 0) {
                       throw new IllegalStateException();
                    }
                }
            }
        }
    }
}
