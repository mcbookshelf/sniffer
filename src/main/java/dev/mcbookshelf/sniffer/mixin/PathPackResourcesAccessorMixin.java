package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.PathPackResourcesAccessor;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

/**
 * Exposes the root directory of a {@link PathPackResources}, so a function can be traced back to it.
 *
 * @author theogiraudet
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
