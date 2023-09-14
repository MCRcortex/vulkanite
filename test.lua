function uniformUpdater(uniform)
    return RaytraceShader
end

if true then
    return uniformUpdater("69")
end

shader = RaytraceShader {
    raygen = ShaderModule{

    },
    raymiss = {
        ShaderModule{

        }
    },
    rayhit = {
        {
            close = ShaderModule{

            },
            any = ShaderModule{

            },
            intersect = nil
        }
    }
}

comp = ComputeShader{
    module = ShaderModule {

    }
}

function uniformUpdater(uniform)

end


-- NOTE: only a single uniform buffer is allowed to exist specifically, only a single cpu -> gpu upload is allowed
pipeline, uniformBuffer = NewPipeline {
    uniform = {
        size = 1024,
        updater = uniformUpdater
    }
}

buff = Buffer {
    size = 1234
}

composite0 = Iris.textures {
    "colortex0"
}

pipeline.traceRays {
    shader = shader,
    bindings = {
        buf = {ACCESS_READ, buff},
        composite0 = {ACCESS_READ_WRITE, composite0}
    },
    shape = SCREEN_SPACE
}

pipeline.dispatch {
    shader = comp,
    bindings = {
        buf = {ACCESS_READ, buff},
        composite0 = {ACCESS_READ_WRITE, composite0}
    },
    shape = SCREEN_SPACE
}



