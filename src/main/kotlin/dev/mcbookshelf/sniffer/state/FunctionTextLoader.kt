package dev.mcbookshelf.sniffer.state

import net.minecraft.resources.Identifier

/**
 * Stores raw source lines of loaded `.mcfunction` files, keyed by identifier.
 *
 * @author XiLaiTL
 */
object FunctionTextLoader {

    private val FUNCTION_TEXT = HashMap<Identifier, List<String>>()

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
