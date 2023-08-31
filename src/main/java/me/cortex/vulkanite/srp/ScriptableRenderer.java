package me.cortex.vulkanite.srp;

import me.cortex.vulkanite.srp.resource.GResource;

import java.util.ArrayList;
import java.util.List;

//TODO: add a synthetic method called CmdUploadBuffer to upload a buffer to the gpu or CmdUploadImage to upload an image
// and or make it so that these are invoked at the start of the frame due to bakery config
public class ScriptableRenderer {
    private final List<GResource> resources = new ArrayList<>();

}
