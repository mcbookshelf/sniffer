package dev.mcbookshelf.sniffer.state

import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the raw source lines of every loaded `.mcfunction` file.
 * It is filled from `CommandFunction.fromLines`, which the reload runs on worker threads, hence the concurrent map.
 *
 * @author XiLaiTL
 * @author theogiraudet
 */
object FunctionTextLoader {

    private val FUNCTION_TEXT = ConcurrentHashMap<Identifier, List<String>>()

    @JvmStatic
    fun functionIds(): Iterable<Identifier> = FUNCTION_TEXT.keys

    @JvmStatic
    fun put(id: Identifier, lines: List<String>) {
        FUNCTION_TEXT[id] = ArrayList(lines)
    }

    @JvmStatic
    fun get(id: Identifier): List<String> =
        FUNCTION_TEXT.getOrDefault(id, emptyList())
}
