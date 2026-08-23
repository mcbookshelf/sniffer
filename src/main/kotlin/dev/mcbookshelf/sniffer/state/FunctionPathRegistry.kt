package dev.mcbookshelf.sniffer.state

import net.minecraft.resources.Identifier
import java.nio.file.Path
import java.util.Optional

/**
 * Maps a function location to the file it was loaded from.
 * Filled by [FunctionPathGetter] on every datapack reload, and read wherever a location has to become a path.
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