package dev.mcbookshelf.sniffer.features.trace

import net.minecraft.commands.Commands
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.mcbookshelf.sniffer.command.SnifferCommand
import net.minecraft.commands.CommandSourceStack

/**
 * The `/trace run <command>` command, which traces the command it redirects to.
 *
 * @author theogiraudet
 */
object TraceCommand : SnifferCommand {

    override fun build(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("trace")
            // forward rather than fork: the traced command keeps its own result and error handling.
            .then(Commands.literal("run").forward(dispatcher.root, TraceModifier, false))
}
