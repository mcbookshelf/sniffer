package dev.mcbookshelf.sniffer.state

import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores raw source lines of loaded `.mcfunction` files, keyed by identifier.
 *
 * @author XiLaiTL
 */
object FunctionTextLoader {

    // Concurrent because Minecraft parses every function of a reload in parallel on the reload executor, so [put] is called from several threads at once.
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
