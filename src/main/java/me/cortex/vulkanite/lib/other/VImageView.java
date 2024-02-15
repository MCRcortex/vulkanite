package me.cortex.vulkanite.lib.other;

import me.cortex.vulkanite.lib.base.VContext;
import me.cortex.vulkanite.lib.base.VObject;
import me.cortex.vulkanite.lib.base.VRef;
import me.cortex.vulkanite.lib.memory.VImage;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VImageView extends VObject {
    private final VContext ctx;
    public final VRef<VImage> image;
    public final long view;
    protected VImageView(VContext ctx, final VRef<VImage> image) {
        this.ctx = ctx;
        this.image = image.addRef();

        int imageViewType = switch (image.get().dimensions) {
            case 1 -> VK_IMAGE_VIEW_TYPE_1D;
            case 2 -> VK_IMAGE_VIEW_TYPE_2D;
            case 3 -> VK_IMAGE_VIEW_TYPE_3D;
            default -> -1;
        };

        try (var stack = stackPush()) {
            LongBuffer view = stack.callocLong(1);
            var vci = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewType(imageViewType)
                    .format(image.get().format)
                    .image(image.get().image());
            vci.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .layerCount(1)
                    .levelCount(1);
            _CHECK_(vkCreateImageView(ctx.device, vci, null, view));
            this.view = view.get(0);
        }
    }

    public boolean isDerivedFrom(VImage image) {
        return this.image.get().image() == image.image();
    }

    static public VRef<VImageView> create(VContext ctx, final VRef<VImage> image) {
        return new VRef<>(new VImageView(ctx, image));
    }

    protected void free() {
        vkDestroyImageView(ctx.device, view, null);
    }
}
