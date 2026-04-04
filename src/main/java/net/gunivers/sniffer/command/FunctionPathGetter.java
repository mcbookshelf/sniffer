package net.gunivers.sniffer.command;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Resource loader that captures the ResourceManager instance during resource reload.
 * This class is used to obtain a reference to the server's ResourceManager
 * which is needed to access datapack function resources during debugging.
 *
 * @author Alumopper
 */
public class FunctionPathGetter implements SimpleSynchronousResourceReloadListener {

    /**
     * Stored reference to the server's resource manager.
     * This is populated during resource reloads and can be used to access datapack resources.
     */
    public static ResourceManager MANAGER;

    /**
     * Returns the identifier for this resource reload listener.
     * @return The identifier for this resource reload listener
     */
    @Override
    public ResourceLocation getFabricId() {
        return ResourceLocation.parse("datapack-debug-loader");
    }

    /**
     * Called during resource reload to store the ResourceManager instance.
     * @param manager The server's resource manager instance
     */
    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        MANAGER = manager;
    }
}
