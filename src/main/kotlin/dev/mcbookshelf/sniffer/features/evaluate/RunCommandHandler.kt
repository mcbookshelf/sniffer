package dev.mcbookshelf.sniffer.features.evaluate

import dev.mcbookshelf.sniffer.dap.ConnectionState
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.util.IsolatedExecution
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Runs a command as the paused function would have run it, and reports what it answered.
 *
 * The command is given the executor of the current scope, so a selector like `@s` means there what it means on the line the debugger stopped on.
 *
 *  @author theogiraudet
 */
class RunCommandHandler(private val scopeManager: ScopeManager) : Handler<RunCommandInput> {

    override val inputType = RunCommandInput::class

    override fun handle(input: RunCommandInput, ctx: Context): Output {
        val source = scopeManager.commandSource(ctx.source)
        val commands = source.server.commands
        val command = Commands.trimOptionalPrefix(input.command.trim())

        val feedback = mutableListOf<String>()
        var success = false
        var result = 0
        val console = source
            .withSource(collectingInto(feedback))
            .withCallback { ok, value -> success = ok; result = value }

        // A command that does not parse reports it to the source rather than throwing, so it lands in the feedback.
        val parse = commands.dispatcher.parse(command, console)
        IsolatedExecution.outsideCurrentContext { commands.performCommand(parse, command) }

        return RunCommandOutput(feedback, success, result)
    }

    private fun collectingInto(feedback: MutableList<String>): CommandSource = object : CommandSource {
        override fun sendSystemMessage(message: Component) {
            feedback += message.string
        }

        override fun acceptsSuccess() = true

        override fun acceptsFailure() = true

        /** The other operators did not ask for this, and it is not a change to the world they would look for. */
        override fun shouldInformAdmins() = false
    }
}

/**
 * The source a debug console runs as.
 *
 * Stopped in a scope, it is the executor of that scope, so a command means there what the paused line means.
 * Otherwise it is the player the debugger is attached to, who brings their position, their dimension and `@s`,
 * which is what someone typing in a console attached to their own game expects.
 * Their permissions are not taken with them: attaching is already gated on being that player, and the host of a
 * world with cheats off would otherwise be refused the very commands the debugger is made of.
 *
 * [fallback] is what is left when the session named nobody, the server's own source.
 */
internal fun ScopeManager.commandSource(fallback: CommandSourceStack): CommandSourceStack {
    val paused = currentScope.map { it.executor }.orElse(null) as? CommandSourceStack
    if (paused != null) return paused

    val player = ConnectionState.attachedPlayer() ?: return fallback
    return player.createCommandSourceStack().withPermission(fallback.permissions())
}
