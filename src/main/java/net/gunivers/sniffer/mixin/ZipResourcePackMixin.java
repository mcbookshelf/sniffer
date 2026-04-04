package net.gunivers.sniffer.mixin;

import com.mojang.logging.LogUtils;
import net.gunivers.sniffer.dap.RealPath;
import net.gunivers.sniffer.dap.ScopeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;

/**
 * Mixin for the ZipResourcePack class to provide debugging capabilities.
 * This mixin allows the debugger to access and track resources within ZIP-based datapacks,
 * which is essential for mapping function paths to their actual locations in the datapack.
 *
 * @author theogiraudet
 */
@Mixin(FilePackResources.class)
public class ZipResourcePackMixin {

    @Shadow
    static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    private String addPrefix(String path) { return ""; }

    @Shadow
    private FilePackResources.SharedZipFileAccess zipFileAccess = null;

/**
     * Overwrites the findResources method to track function file paths.
     * This method enhances the original by capturing the physical file paths
     * of .mcfunction files and associating them with their identifiers,
     * which is essential for source mapping in the debugger.
     *
     * @author theogiraudet
     * @reason to access to the identifier as well as the foundPath
     * @param namespace The resource namespace
     * @param consumer The consumer to process found resources
     */
    @Overwrite
    public void listResources(PackType type, String namespace, String prefix, PackResources.ResourceOutput consumer) {
        var zipFileOpt = zipFileAccess.getOrCreateZipFile();
        if (zipFileOpt != null) {
            Enumeration<? extends ZipEntry> enumeration = zipFileOpt.entries();
            String var10001 = type.getDirectory();
            String string = this.addPrefix(var10001 + "/" + namespace + "/");
            String string2 = string + prefix + "/";

            while(enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                if (!zipEntry.isDirectory()) {
                    String string3 = zipEntry.getName();
                    if (string3.startsWith(string2)) {
                        String string4 = string3.substring(string.length());
                        ResourceLocation identifier = ResourceLocation.tryBuild(namespace, string4);
                        if (identifier != null) {
                            if(identifier.getPath().endsWith(".mcfunction")) {
                                ScopeManager.get().savePath(Path.of(zipFileOpt.getName(), string3), identifier, RealPath.Kind.ZIP);
                            }
                            consumer.accept(identifier, IoSupplier.create(zipFileOpt, zipEntry));
                        } else {
                            LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, string4);
                        }
                    }
                }
            }
        }
    }
}
