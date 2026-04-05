package dev.mcbookshelf.sniffer.state

import net.minecraft.commands.CommandSourceStack
import org.slf4j.LoggerFactory

/**
 * Triggers a breakpoint at the current execution position.
 *
 * Freezes the tick-rate manager, notifies DAP stop consumers via
 * [DebugEventBus], and enables debugging mode on [SteppingState].
 *
 * Called from [dev.mcbookshelf.sniffer.mixin.UnboundDebugMixin] on the
 * server thread, immediately before [ExecutionLock.pauseExecution].
 */
object BreakpointTrigger {

    private val LOGGER = LoggerFactory.getLogger("sniffer")
    private const val BREAKPOINT_REASON = "breakpoint"

    @JvmStatic
    fun trigger(source: CommandSourceStack) {
        try {
            source.server.tickRateManager().setFrozen(true)

            val scope = ScopeManager.get().currentScope
            val fn = scope.map { it.function }.orElse("")
            val line = scope.map { it.line }.orElse(-1)
            val bpId = BreakpointManager.getBreakpointId(fn, line).orElse(-1)
            DebugEventBus.fireStop(bpId, BREAKPOINT_REASON)

            SteppingState.isDebugging = true
            SteppingState.currSource = source

            LOGGER.debug("Breakpoint triggered at {}:{}", fn, line)
        } catch (e: Exception) {
            LOGGER.error("Error triggering breakpoint", e)
        }
    }
}
