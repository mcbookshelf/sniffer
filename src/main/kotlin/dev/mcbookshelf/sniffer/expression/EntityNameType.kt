package dev.mcbookshelf.sniffer.expression

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import dev.mcbookshelf.sniffer.util.Extension.expect
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.MessageArgument

/**
 * Parses `name <selector>`, yielding the display name of the entity it matches.
 *
 * @author Alumopper
 */
class EntityNameType: ArgumentType<EntityNameType.Name>{
    override fun parse(reader: StringReader): Name {
        reader.skipWhitespace()
        reader.expect("name")
        reader.skipWhitespace()
        val name = MessageArgument.message().parse(reader)
        return Name(name)
    }

    class Name(val msg: MessageArgument.Message): DebugData {
        override fun get(source: CommandSourceStack): Any {
            return msg.toComponent(source, true)
        }

    }

}
