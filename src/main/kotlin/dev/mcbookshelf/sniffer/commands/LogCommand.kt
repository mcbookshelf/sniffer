package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.server.permissions.Permissions
import net.minecraft.network.chat.Component

/**
 * The `/log` command, which evaluates a log expression and broadcasts the result to every player.
 *
 * @author Alumopper
 */
object LogCommand {
    @JvmStatic
    fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            dispatcher.register(
                literal<CommandSourceStack>("log")
                    .requires{it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}
                    .then(argument("log", LogArgumentType())
                        .executes {
                            val log = LogArgumentType.getLog(it, "log")
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