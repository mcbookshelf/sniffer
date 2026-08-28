package dev.mcbookshelf.sniffer.expression

import net.minecraft.commands.CommandSourceStack

/**
 * A [DebugData] that already holds its value, such as a literal.
 *
 * @author Alumopper
 */
class PlainData(private val value: Any): DebugData {
    override fun get(source: CommandSourceStack): Any {
        return value
    }
}
