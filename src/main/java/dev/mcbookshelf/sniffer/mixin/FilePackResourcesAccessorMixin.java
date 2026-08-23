package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.FilePackResourcesAccessor;
import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the zip file and the prefix of a {@link FilePackResources}, so a function can be traced back to it.
 *
 * @author theogiraudet
 * @author Alumopper
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
