package net.gunivers.sniffer.mixin;

import com.google.common.base.Joiner;
import com.mojang.logging.LogUtils;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import net.gunivers.sniffer.dap.RealPath;
import net.gunivers.sniffer.dap.ScopeManager;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Mixin for the DirectoryResourcePack class to enhance resource discovery for debugging.
 * This mixin overwrites the findResources method to capture file paths for datapack functions,
 * allowing the debugger to map function identifiers to their actual file locations.
 *
 * @author theogiraudet
 */
@Mixin(PathPackResources.class)
public class PathPackResourcesPackMixin {

    @Shadow
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    private static final Joiner PATH_JOINER = Joiner.on("/");

/**
     * Overwrites the findResources method to track function file paths.
     * This method enhances the original by capturing the physical file paths
     * of .mcfunction files and associating them with their identifiers,
     * which is essential for source mapping in the debugger.
     *
     * @author theogiraudet
     * @reason to access to the identifier as well as the foundPath
     * @param namespace The resource namespace
     * @param path The base path to search in
     * @param prefixSegments The path prefix segments
     * @param consumer The consumer to process found resources
     */
    @Overwrite
    public static void listPath(String namespace, Path path, List<String> prefixSegments, PackResources.ResourceOutput consumer) {
        Path path2 = FileUtil.resolvePath(path, prefixSegments);

        try (Stream<Path> stream = Files.find(path2, Integer.MAX_VALUE, (path2x, attributes) -> attributes.isRegularFile())) {
            stream.forEach((foundPath) -> {
                String string2 = PATH_JOINER.join(path.relativize(foundPath));
                ResourceLocation identifier = ResourceLocation.tryBuild(namespace, string2);
                if (identifier == null) {
                    Util.logAndPauseIfInIde(String.format(Locale.ROOT, "Invalid path in pack: %s:%s, ignoring", namespace, string2));
                } else {
                    if(identifier.getPath().endsWith(".mcfunction")) {
                        ScopeManager.get().savePath(foundPath, identifier, RealPath.Kind.DIRECTORY);
                    }
                    consumer.accept(identifier, IoSupplier.create(foundPath));
                }

            });
        } catch (NotDirectoryException | NoSuchFileException ignored) {
        } catch (IOException iOException) {
            LOGGER.error("Failed to list path {}", path2, iOException);
        }

    }

}
