package dev.mcbookshelf.sniffer.expression

import net.minecraft.commands.CommandSourceStack
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component

/**
 * A value an expression can yield, resolved against the command source when it is evaluated.
 *
 * @author Alumopper
 */
interface DebugData {
    fun get(source: CommandSourceStack): Any

    companion object {
        fun toText(any: Any): Component{
            return when(any){
                is Tag -> NbtUtils.toPrettyComponent(any)
                is Component -> any
                else -> Component.literal(any.toString())
            }
        }
    }
}
