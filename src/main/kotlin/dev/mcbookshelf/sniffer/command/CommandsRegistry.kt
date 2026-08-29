package dev.mcbookshelf.sniffer.command

import dev.mcbookshelf.sniffer.features.assertion.AssertCommand
import dev.mcbookshelf.sniffer.features.breakpoints.BreakPointCommand
import dev.mcbookshelf.sniffer.features.debugmode.DebugModeCommand
import dev.mcbookshelf.sniffer.features.jvmtimer.JvmtimerCommand
import dev.mcbookshelf.sniffer.features.log.LogCommand
import dev.mcbookshelf.sniffer.features.trace.TraceCommand
import dev.mcbookshelf.sniffer.features.watch.WatchCommand
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.permissions.Permissions

/**
 * Lists the commands Sniffer adds and registers them all on the one callback.
 * Adding a command means adding one line here, the way an action is added to `buildHandlers`.
 *
 * @author theogiraudet
 */
object CommandsRegistry {

    private val commands: List<SnifferCommand> = listOf(
        BreakPointCommand,
        LogCommand,
        AssertCommand,
        JvmtimerCommand,
        WatchCommand,
        DebugModeCommand,
        TraceCommand,
    )

    /**
     * Registers every command, each gated behind the operator level.
     * The gate is applied here rather than by each command, so a command added to the list cannot forget it.
     */
    @JvmStatic
    fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            for (command in commands) {
                dispatcher.register(
                    command.build(dispatcher)
                        .requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                )
            }
        }
    }
}
