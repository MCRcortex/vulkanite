package me.cortex.vulkanite.lib.shader.reflection;

import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.spvc.Spv.SpvDecorationBinding;
import static org.lwjgl.util.spvc.Spvc.*;

public class ShaderReflection {
    public ShaderReflection(ByteBuffer spirv) {
        try (var stack = stackPush()) {
            //Create context
            var ptr = stack.mallocPointer(1);
            var ptr2 = stack.mallocPointer(1);
            _CHECK_(spvc_context_create(ptr));
            long context = ptr.get(0);

            //Parse the spir-v
            _CHECK_(spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining()>>2, ptr));
            long ir = ptr.get(0);

            // Hand it off to a compiler instance and give it ownership of the IR.
            _CHECK_(spvc_context_create_compiler(context, SPVC_BACKEND_NONE, ir, SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, ptr));
            long compiler = ptr.get(0);

            // Create resources from spir-v
            _CHECK_(spvc_compiler_create_shader_resources(compiler, ptr));
            long resources = ptr.get(0);

            //Get reflection data
            for (var type : ResourceType.values()) {
                _CHECK_(spvc_resources_get_resource_list_for_type(resources, type.id, ptr, ptr2));
                var reflectedResources = SpvcReflectedResource.create(ptr.get(0), (int) ptr2.get(0));
                for (var reflect : reflectedResources) {
                    System.err.println(type.name()+" -> "+reflect.nameString() + " @ " + spvc_compiler_get_decoration(compiler, reflect.id(), SpvDecorationBinding));
                }
            }
            System.err.println("");
            //Destroy everything
            spvc_context_destroy(context);
        }
    }

    private static void _CHECK_(int status) {
        if (status != 0) {
            throw new IllegalStateException("Got status: " + status);
        }
    }
}
