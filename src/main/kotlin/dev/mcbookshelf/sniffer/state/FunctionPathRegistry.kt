package dev.mcbookshelf.sniffer.state

import net.minecraft.resources.Identifier
import java.nio.file.Path
import java.util.Optional

/**
 * Global registry mapping Minecraft function identifiers (`namespace:path`)
 * to their physical filesystem paths.
 *
 * Populated by [FunctionPathGetter] on datapack reload and queried by
 * [BreakpointManager] and [ScopeManager.DebugScope] for path resolution.
 *
 * @author theogiraudet
 */
object FunctionPathRegistry {

    private val paths = HashMap<String, RealPath>()

    fun savePath(path: Path, id: Identifier, kind: RealPath.Kind) {
        val location = id.namespace + ":" + id.path.substring("function/".length, id.path.length - ".mcfunction".length)
        paths.putIfAbsent(location, RealPath(path.toAbsolutePath().toString(), kind))
    }

    fun getPath(mcpath: String): Optional<String> =
        Optional.ofNullable(paths[mcpath]).map { it.path }

    fun getRealPath(mcpath: String): RealPath? = paths[mcpath]

    fun clear() {
        paths.clear()
    }
}