package me.cortex.vulkanite.lib.other;

import net.coderbot.iris.gl.texture.InternalTextureFormat;

import static org.lwjgl.opengl.GL11C.GL_RGB8;
import static org.lwjgl.opengl.GL11C.GL_RGBA16;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL30C.GL_R16F;
import static org.lwjgl.vulkan.VK10.*;

public class FormatConverter {
    public static int getVkFormatFromGl(InternalTextureFormat format) {
        // TODO: Support 3 component types

        return switch (format.getGlFormat()) {
            // Weird formats
            case GL_R11F_G11F_B10F -> VK_FORMAT_B10G11R11_UFLOAT_PACK32;
            case GL_RGB10_A2, GL_RGB10 -> VK_FORMAT_A2B10G10R10_UNORM_PACK32;
            case GL_RGB5, GL_RGB5_A1 -> VK_FORMAT_A1R5G5B5_UNORM_PACK16;
            case GL_RGB9_E5 -> VK_FORMAT_E5B9G9R9_UFLOAT_PACK32;

            // Floats
            case GL_RGB32F, GL_RGBA32F -> VK_FORMAT_R32G32B32A32_SFLOAT;
            case GL_RGB16F, GL_RGBA16F -> VK_FORMAT_R16G16B16A16_SFLOAT;
            case GL_RG16F -> VK_FORMAT_R16G16_SFLOAT;
            case GL_R16F -> VK_FORMAT_R16_SFLOAT;

            // Unorms
            case GL_RGB16, GL_RGBA16 -> VK_FORMAT_R16G16B16A16_UNORM;
            case GL_RG16 -> VK_FORMAT_R16G16_UNORM;
            case GL_R16 -> VK_FORMAT_R16_UNORM;
            case GL_RGBA, GL_RGB8, GL_RGBA8 -> VK_FORMAT_R8G8B8A8_UNORM;
            case GL_RG8 -> VK_FORMAT_R8G8_UNORM;
            case GL_R8 -> VK_FORMAT_R8_UNORM;

            // Ints
            case GL_RGB32I, GL_RGBA32I -> VK_FORMAT_R32G32B32A32_SINT;
            case GL_RG32I -> VK_FORMAT_R32G32_SINT;
            case GL_R32I -> VK_FORMAT_R32_SINT;
            case GL_RGB32UI, GL_RGBA32UI -> VK_FORMAT_R32G32B32A32_UINT;
            case GL_RG32UI -> VK_FORMAT_R32G32_UINT;
            case GL_R32UI -> VK_FORMAT_R32_UINT;
            case GL_RGB16I, GL_RGBA16I -> VK_FORMAT_R16G16B16A16_SINT;
            case GL_RG16I -> VK_FORMAT_R16G16_SINT;
            case GL_R16I -> VK_FORMAT_R16_SINT;
            case GL_RGB16UI, GL_RGBA16UI -> VK_FORMAT_R16G16B16A16_UINT;
            case GL_RG16UI -> VK_FORMAT_R16G16_UINT;
            case GL_R16UI -> VK_FORMAT_R16_UINT;
            case GL_RGB8I, GL_RGBA8I -> VK_FORMAT_R8G8B8A8_SINT;
            case GL_RG8I -> VK_FORMAT_R8G8_SINT;
            case GL_R8I -> VK_FORMAT_R8_SINT;
            case GL_RGB8UI, GL_RGBA8UI -> VK_FORMAT_R8G8B8A8_UINT;
            case GL_RG8UI -> VK_FORMAT_R8G8_UINT;
            case GL_R8UI -> VK_FORMAT_R8_UINT;

            default -> {
                throw new IllegalArgumentException("No known conversion to VK type for GL type " + format + " (" + format.getGlFormat() + ").");
            }
        };
    }

    public static InternalTextureFormat findFormatFromGlFormat(int glFormat) {
        InternalTextureFormat[] availableFormats = InternalTextureFormat.values();

        for (InternalTextureFormat format : availableFormats) {
            if(format.getGlFormat() == glFormat) {
                return format;
            }
        }

        return null;
    }
}
