package dev.mcbookshelf.sniffer.commands

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.input.SetDebugModeInput
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions

/**
 * The `/debugmode` command, which shows or hides the HUD overlay of the player running it.
 *
 * @author theogiraudet
 */
object DebugModeCommand {

    @JvmStatic
    fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("debugmode")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                    .then(
                        Commands.literal("enable")
                            .executes { context ->
                                SnifferDispatcher.get().dispatch(
                                    SetDebugModeInput(true),
                                    Context(context.source, context.source.server)
                                )
                                context.source.sendSuccess(
                                    { Component.translatable("sniffer.commands.debugmode.enable") },
                                    true
                                )
                                1
                            }
                    )
                    .then(
                        Commands.literal("disable")
                            .executes { context ->
                                SnifferDispatcher.get().dispatch(
                                    SetDebugModeInput(false),
                                    Context(context.source, context.source.server)
                                )
                                context.source.sendSuccess(
                                    { Component.translatable("sniffer.commands.debugmode.disable") },
                                    true
                                )
                                1
                            }
                    )
            )
        }
    }
}
