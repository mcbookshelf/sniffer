package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.PathPackResourcesAccessor;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

/**
 * Accessor-only mixin on {@link PathPackResources} — exposes the root
 * directory path for function-path resolution. No method overrides.
 */
@Mixin(PathPackResources.class)
public class PathPackResourcesAccessorMixin implements PathPackResourcesAccessor {

    @Shadow @Final
    private Path root;

    @Override
    public Path sniffer$getRoot() {
        return root;
    }
}
