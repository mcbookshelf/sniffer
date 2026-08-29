package dev.mcbookshelf.sniffer.features.debugmode

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import net.minecraft.commands.Commands
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.mcbookshelf.sniffer.command.SnifferCommand
import net.minecraft.commands.CommandSourceStack
import dev.mcbookshelf.sniffer.chat.SnifferChat

/**
 * The `/debugmode` command, which shows or hides the HUD overlay of the player running it.
 *
 * @author theogiraudet
 */
object DebugModeCommand : SnifferCommand {

    override fun build(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("debugmode")
            .then(
                Commands.literal("enable")
                    .executes { context ->
                        SnifferDispatcher.get().dispatch(
                            SetDebugModeInput(true),
                            Context(context.source)
                        )
                        SnifferChat.reply(
                            context.source,
                            "sniffer.commands.debugmode.enable",
                            informAdmins = true
                        )
                        1
                    }
            )
            .then(
                Commands.literal("disable")
                    .executes { context ->
                        SnifferDispatcher.get().dispatch(
                            SetDebugModeInput(false),
                            Context(context.source)
                        )
                        SnifferChat.reply(
                            context.source,
                            "sniffer.commands.debugmode.disable",
                            informAdmins = true
                        )
                        1
                    }
            )
}
