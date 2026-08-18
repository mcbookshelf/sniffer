package dev.mcbookshelf.sniffer.state

import net.minecraft.commands.CommandSourceStack
import org.slf4j.LoggerFactory

/**
 * Triggers a pause at the current execution position.
 *
 * Notifies DAP stop consumers via [DebugEventBus] and enables debugging mode on [SteppingState].
 * Called from [dev.mcbookshelf.sniffer.mixin.UnboundDebugMixin] on the server thread, immediately before [PausedExecutionStore.stash].
 *
 * The world is not frozen at a breakpoint: only the paused datapack function is suspended, while ticks, players and other commands keep running.
 */
object BreakpointTrigger {

    private val LOGGER = LoggerFactory.getLogger("sniffer")

    /** DAP stop reasons, reported verbatim to the client. */
    const val BREAKPOINT_REASON = "breakpoint"
    const val STEP_REASON = "step"

    /**
     * @param reason why execution stopped, as the DAP client is told.
     *   Only the caller can tell a breakpoint hit from a step landing, and a client shows the two differently.
     */
    @JvmStatic
    @JvmOverloads
    fun trigger(source: CommandSourceStack, reason: String = BREAKPOINT_REASON) {
        try {
            val scope = ScopeManager.get().currentScope
            val fn = scope.map { it.function }.orElse("")
            val line = scope.map { it.line }.orElse(-1)
            val bpId = BreakpointManager.getBreakpointId(fn, line).orElse(-1)
            DebugEventBus.fireStop(bpId, reason)

            SteppingState.setDebugging(true)
            SteppingState.currSource = source

            LOGGER.debug("Execution stopped ({}) at {}:{}", reason, fn, line)
        } catch (e: Exception) {
            LOGGER.error("Error triggering breakpoint", e)
        }
    }
}
