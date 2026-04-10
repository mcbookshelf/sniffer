package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.functions.CommandFunction

interface CommandFunctionUniqueAccessors {
    var debugTags: ArrayList<String>

    companion object {
        @JvmStatic
        fun of(function: CommandFunction<*>): CommandFunctionUniqueAccessors =
            function as CommandFunctionUniqueAccessors
    }
}
