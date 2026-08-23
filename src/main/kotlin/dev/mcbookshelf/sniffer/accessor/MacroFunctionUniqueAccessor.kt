package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.functions.MacroFunction

/**
 * Exposes the line mapping attached to a macro when it was parsed.
 *
 * @author Alumopper
 * @author theogiraudet
 */
interface MacroFunctionUniqueAccessor {
    var lineMapping: List<Int>?

    companion object {
        @JvmStatic
        fun of(function: MacroFunction<*>): MacroFunctionUniqueAccessor =
            function as MacroFunctionUniqueAccessor
    }
}
