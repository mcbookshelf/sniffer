package dev.mcbookshelf.sniffer.state

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import dev.mcbookshelf.sniffer.accessor.FilePackResourcesAccessor
import dev.mcbookshelf.sniffer.accessor.PathPackResourcesAccessor
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Resource reload listener that resolves the physical filesystem paths of
 * all loaded `.mcfunction` files after a datapack reload.
 *
 * Iterates the loaded resources after reload and uses accessor interfaces
 * to get pack root paths.
 *
 * @author theogiraudet
 */
class FunctionPathGetter : SimpleSynchronousResourceReloadListener {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("sniffer")

        @JvmField
        var MANAGER: ResourceManager? = null
    }

    override fun getFabricId(): Identifier =
        Identifier.parse("datapack-debug-loader")

    override fun onResourceManagerReload(manager: ResourceManager) {
        MANAGER = manager
        FunctionPathRegistry.clear()

        val functions = manager.listResources("function") { id ->
            id.path.endsWith(".mcfunction")
        }

        for ((id, resource) in functions) {
            resolveAndSavePath(id, resource)
        }

        LOGGER.debug("Resolved {} function paths for debugging", functions.size)
    }

    private fun resolveAndSavePath(id: Identifier, resource: Resource) {
        try {
            val pack = resource.source()

            when (pack) {
                is PathPackResources -> {
                    val root = (pack as PathPackResourcesAccessor).`sniffer$getRoot`()
                    val filePath = root.resolve(PackType.SERVER_DATA.directory)
                        .resolve(id.namespace)
                        .resolve(id.path)
                    FunctionPathRegistry.savePath(filePath, id, RealPath.Kind.DIRECTORY)
                }
                is FilePackResources -> {
                    val accessor = pack as FilePackResourcesAccessor
                    val zipFile = accessor.`sniffer$getZipFileAccess`().orCreateZipFile
                    if (zipFile != null) {
                        val prefix = accessor.`sniffer$getPrefix`()
                        val internalPath = (if (prefix.isEmpty()) "" else "$prefix/") +
                            "${PackType.SERVER_DATA.directory}/${id.namespace}/${id.path}"
                        val zipPath = Path.of(zipFile.name, internalPath)
                        FunctionPathRegistry.savePath(zipPath, id, RealPath.Kind.ZIP)
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to resolve path for function {}: {}", id, e.message)
        }
    }
}
