package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.functions.CommandFunction

/**
 * Exposes the debug tags a function declares with `#@`.
 *
 * @author Alumopper
 * @author theogiraudet
 */
interface CommandFunctionUniqueAccessors {
    var debugTags: ArrayList<String>

    companion object {
        @JvmStatic
        fun of(function: CommandFunction<*>): CommandFunctionUniqueAccessors =
            function as CommandFunctionUniqueAccessors
    }
}
