package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.FilePackResourcesAccessor;
import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Accessor-only mixin on {@link FilePackResources} — exposes the zip file
 * access and prefix for function-path resolution. No method overrides.
 */
@Mixin(FilePackResources.class)
public class FilePackResourcesAccessorMixin implements FilePackResourcesAccessor {

    @Shadow @Final
    private FilePackResources.SharedZipFileAccess zipFileAccess;

    @Shadow @Final
    private String prefix;

    @Override
    public FilePackResources.SharedZipFileAccess sniffer$getZipFileAccess() {
        return zipFileAccess;
    }

    @Override
    public String sniffer$getPrefix() {
        return prefix;
    }
}
