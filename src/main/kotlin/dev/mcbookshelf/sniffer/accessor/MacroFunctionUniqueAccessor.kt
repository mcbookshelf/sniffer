package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.functions.MacroFunction

interface MacroFunctionUniqueAccessor {
    var lineMapping: List<Int>?

    companion object {
        @JvmStatic
        fun of(function: MacroFunction<*>): MacroFunctionUniqueAccessor =
            function as MacroFunctionUniqueAccessor
    }
}
