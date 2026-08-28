package dev.mcbookshelf.sniffer.features.breakpoints

import net.minecraft.commands.CommandSourceStack
import org.slf4j.LoggerFactory
import dev.mcbookshelf.sniffer.dap.DebugEventBus
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.features.stepping.SteppingState

/**
 * Announces a pause at the current execution position.
 * It fires the stop event the DAP client listens to and turns debugging on.
 * Called on the server thread, right before the execution is stashed.
 *
 * @author theogiraudet
 */
object BreakpointTrigger {

    private val LOGGER = LoggerFactory.getLogger("sniffer")

    /** DAP stop reasons, reported verbatim to the client. */
    const val BREAKPOINT_REASON = "breakpoint"
    const val STEP_REASON = "step"
    const val PAUSE_REASON = "pause"

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
            // A pause lands wherever execution happened to be, so no breakpoint was hit even if one sits on that line.
            val bpId = if (reason == PAUSE_REASON) -1 else BreakpointManager.getBreakpointId(fn, line).orElse(-1)
            DebugEventBus.fireStop(bpId, reason)

            SteppingState.setDebugging(true)
            SteppingState.currSource = source

            LOGGER.debug("Execution stopped ({}) at {}:{}", reason, fn, line)
        } catch (e: Exception) {
            LOGGER.error("Error triggering breakpoint", e)
        }
    }
}
