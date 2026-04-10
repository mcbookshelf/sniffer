package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.server.permissions.Permissions
import net.minecraft.network.chat.Component

/**
 * Registers the `/log` command that evaluates a [LogArgumentType] expression
 * and broadcasts the result to all players.
 */
object LogCommand {
    @JvmStatic
    fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            dispatcher.register(
                literal<CommandSourceStack?>("log")
                    .requires{it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}
                    .then(argument("log", LogArgumentType())
                        .executes {
                            val log = LogArgumentType.getLog(it, "log")
                            //build output text
                            val text = Component.empty()
                            for (l in log.logs){
                                val data = l.get(it.source)
                                text.append(DebugData.toText(data))
                            }
                            it.source.server.playerList.broadcastSystemMessage(text, false)
                            return@executes 1
                        }
                    )
            )
        }
    }

}