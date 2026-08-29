package dev.mcbookshelf.sniffer.features.log

import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.network.chat.Component
import dev.mcbookshelf.sniffer.expression.DebugData
import dev.mcbookshelf.sniffer.expression.LogArgumentType
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.mcbookshelf.sniffer.command.SnifferCommand
import dev.mcbookshelf.sniffer.chat.SnifferChat

/**
 * The `/log` command, which evaluates a log expression and broadcasts the result to every player.
 *
 * @author Alumopper
 */
object LogCommand : SnifferCommand {
    override fun build(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal<CommandSourceStack>("log")
            .then(argument("log", LogArgumentType())
                .executes {
                    val log = LogArgumentType.getLog(it, "log")
                    val text = Component.empty()
                    for (l in log.logs){
                        val data = l.get(it.source)
                        text.append(DebugData.toText(data))
                    }
                    SnifferChat.broadcast(it.source.server, text)
                    return@executes 1
                }
            )

}