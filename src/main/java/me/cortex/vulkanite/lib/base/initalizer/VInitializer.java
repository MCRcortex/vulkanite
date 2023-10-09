package me.cortex.vulkanite.lib.base.initalizer;

import me.cortex.vulkanite.lib.base.VContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static me.cortex.vulkanite.lib.other.VUtil._CHECK_;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

public class VInitializer {
    private final VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private int queueCount;
    private long debugMessenger = 0;
    public VInitializer(String appName, String engineName, int major, int minor, String[] extensions, String[] layers) {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .apiVersion(VK_MAKE_VERSION(major, minor, 0))
                    .pApplicationName(memUTF8(appName))
                    .pEngineName(memUTF8(engineName));

            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(stack.pointers(Arrays.stream(extensions).map(stack::UTF8).toArray(ByteBuffer[]::new)))
                    .ppEnabledLayerNames(stack.pointers(Arrays.stream(layers).map(stack::UTF8).toArray(ByteBuffer[]::new)));

            PointerBuffer result = stack.pointers(0);
            _CHECK_(vkCreateInstance(instanceCreateInfo, null, result));

            instance = new VkInstance(result.get(0), instanceCreateInfo);

            if (Arrays.asList(extensions).contains(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)) {
                VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                        .sType$Default()
                        .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                        .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                        .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                            VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                            System.err.println("Validation layer: " + callbackData.pMessageString());
                            Thread.dumpStack();
                            return VK_FALSE;
                        });

                var pDebugMessenger = stack.mallocLong(1);
                _CHECK_(vkCreateDebugUtilsMessengerEXT(instance, debugCreateInfo, null, pDebugMessenger));
                debugMessenger = pDebugMessenger.get(0);
                // Runtime.getRuntime().addShutdownHook(new Thread(() -> vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null)));
            }
        }
    }

    public void findPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer devices = getPhysicalDevices(stack);
            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(new VkPhysicalDevice(devices.get(i), instance), props);
                System.out.println(props.deviceNameString());
                physicalDevice = new VkPhysicalDevice(devices.get(i), instance);
                break;
            }
        }
    }

    //TODO: add nice queue creation system
    public void createDevice(List<String> extensions, List<String> layers, float[] queuePriorities, Consumer<VkPhysicalDeviceFeatures> deviceFeatures, List<Function<MemoryStack, Struct>> applicators, List<Consumer<Struct>> postApplicators) {
        var deviceExtensions = new HashSet<>(getDeviceExtensionStrings(physicalDevice));
        for (var extension : extensions) {
            if (!deviceExtensions.contains(extension)) {
                throw new IllegalStateException("Physical device is missing extension: " + extension);
            }
        }

        try (MemoryStack stack = stackPush()) {

            queueCount = queuePriorities.length;
            var queueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pQueuePriorities(stack.floats(queuePriorities))
                    .queueFamilyIndex(0);

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .ppEnabledExtensionNames(stack.pointers(extensions.stream().map(stack::UTF8).toArray(ByteBuffer[]::new)))
                    .ppEnabledLayerNames(stack.pointers(layers.stream().map(stack::UTF8).toArray(ByteBuffer[]::new)))
                    .pQueueCreateInfos(queueCreateInfos);

            if (deviceFeatures != null) {
                var features = VkPhysicalDeviceFeatures.calloc(stack);
                deviceFeatures.accept(features);
                createInfo.pEnabledFeatures(features);
            } else {
                createInfo.pEnabledFeatures(null);
            }

            long chain = createInfo.address();
            var deviceProperties2 = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            if (postApplicators.size() != applicators.size()) {
                throw new IllegalStateException("Post applicators and applicators must be the same size");
            }
            for (int i = 0; i < applicators.size(); i++) {
                var applicator = applicators.get(i);
                Struct feature = applicator.apply(stack);
                deviceProperties2.pNext(feature.address());
                vkGetPhysicalDeviceFeatures2(physicalDevice, deviceProperties2);
                postApplicators.get(i).accept(feature);
                long next = feature.address();
                MemoryUtil.memPutAddress(chain+8, next);
                chain = next;
            }

            PointerBuffer pDevice = stack.callocPointer(1);
            _CHECK_(vkCreateDevice(physicalDevice, createInfo, null, pDevice));
            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);
        }
    }

    private static VkLayerProperties.Buffer getInstanceLayers(MemoryStack stack) {
        int[] res = new int[1];
        _CHECK_(vkEnumerateInstanceLayerProperties(res, null));
        VkLayerProperties.Buffer layerProperties = VkLayerProperties.calloc(res[0], stack);
        _CHECK_(vkEnumerateInstanceLayerProperties(res, layerProperties));
        if (res[0] != layerProperties.capacity())
            throw new IllegalStateException();
        return layerProperties;
    }

    private static VkExtensionProperties.Buffer getInstanceExtensions(MemoryStack stack) {
        int[] res = new int[1];
        _CHECK_(vkEnumerateInstanceExtensionProperties((String) null, res, null));
        VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(res[0], stack);
        _CHECK_(vkEnumerateInstanceExtensionProperties((String) null, res, extensionProperties));
        if (res[0] != extensionProperties.capacity())
            throw new IllegalStateException();
        return extensionProperties;
    }

    private PointerBuffer getPhysicalDevices(MemoryStack stack) {
        int[] res = new int[1];
        _CHECK_(vkEnumeratePhysicalDevices(instance, res, null));
        PointerBuffer devices = stack.callocPointer(res[0]);
        _CHECK_(vkEnumeratePhysicalDevices(instance, res, devices));
        if (res[0] != devices.capacity())
            throw new IllegalStateException();
        return devices;
    }

    private List<String> getDeviceExtensionStrings(VkPhysicalDevice device) {
        List<String> extensions = new ArrayList<>();
        try (var stack = stackPush()) {
            var eb = getDeviceExtensions(stack, device);
            for (var extension : eb) {
                extensions.add(extension.extensionNameString());
            }
        }
        return extensions;
    }

    private VkExtensionProperties.Buffer getDeviceExtensions(MemoryStack stack, long device) {
        return getDeviceExtensions(stack, new VkPhysicalDevice(device, instance));
    }

    private VkExtensionProperties.Buffer getDeviceExtensions(MemoryStack stack, VkPhysicalDevice device) {
        int[] res = new int[1];
        _CHECK_(vkEnumerateDeviceExtensionProperties(device, (String) null, res, null));
        VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(res[0], stack);
        _CHECK_(vkEnumerateDeviceExtensionProperties(device, (String) null, res, extensionProperties));
        if (res[0] != extensionProperties.capacity())
            throw new IllegalStateException();
        return extensionProperties;
    }

    public VContext createContext() {
        //TODO:FIXME: DONT HARDCODE THE FACT IT HAS DEVICE ADDRESSES
        return new VContext(device, queueCount, true, debugMessenger != 0);
    }
}
