package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.memory.VImage;
import me.cortex.vulkanite.lib.other.VImageView;

import java.util.function.Supplier;

public class SharedImageViewTracker {
    private final VContext ctx;
    private final Supplier<VImage> supplier;
    private VImageView view;
    public SharedImageViewTracker(VContext ctx, Supplier<VImage> imageSupplier) {
        this.supplier = imageSupplier;
        this.ctx = ctx;
    }

    //NOTE: getting the image doesnt invalidate/check for a different image
    public VImage getImage() {
        if (view != null) {
            return view.image;
        }
        return null;
    }

    public VImageView getView() {
        return getView(this.supplier);
    }

    public VImageView getView(Supplier<VImage> imageSupplier) {
        VImage image = imageSupplier.get();
        if (this.view == null || this.view.image != image) {
            //TODO: move this to like a fence free that you pass in via an arg
            if (view != null) {
                Vulkanite.INSTANCE.addSyncedCallback(view::free);
                view = null;
            }
            if (image != null) {
                this.view = new VImageView(ctx, image);
            }
        }
        return view;
    }

    public void free() {
        if (view != null) {
            Vulkanite.INSTANCE.addSyncedCallback(view::free);
            view = null;
        }
    }
}
