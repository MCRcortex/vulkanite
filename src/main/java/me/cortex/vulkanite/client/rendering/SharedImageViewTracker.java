package me.cortex.vulkanite.client.rendering;

import me.cortex.vulkanite.client.Vulkanite;
import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VGImage;
import me.cortex.vulkanite.lib.memory.VImage;
import me.cortex.vulkanite.lib.other.VImageView;

import java.util.function.Supplier;

public class SharedImageViewTracker {
    private final VContext ctx;
    private final Supplier<VRef<VGImage>> supplier;
    private VRef<VImageView> view;
    public SharedImageViewTracker(VContext ctx, Supplier<VRef<VGImage>> imageSupplier) {
        this.supplier = imageSupplier;
        this.ctx = ctx;
    }

    //NOTE: getting the image doesnt invalidate/check for a different image
    public VRef<VImage> getImage() {
        if (view != null) {
            return view.get().image.addRef();
        }
        return null;
    }

    public VRef<VImageView> getView() {
        return getView(this.supplier);
    }

    public VRef<VImageView> getView(Supplier<VRef<VGImage>> imageSupplier) {
        VRef<VGImage> image = imageSupplier.get();
        if (view == null || (!view.get().isDerivedFrom(image.get()))) {
            //TODO: move this to like a fence free that you pass in via an arg
            if (image != null) {
                view = VImageView.create(ctx, new VRef<>(image.get()));
            } else {
                view = null;
            }
        }
        return view == null ? null : view.addRef();
    }
}
