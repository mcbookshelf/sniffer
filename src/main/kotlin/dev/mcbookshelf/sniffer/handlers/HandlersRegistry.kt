package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.EvaluationVariableStore
import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.state.BreakpointManager

/**
 * Central factory that wires every [Handler] with its long-lived
 * service dependencies and returns the full list.
 *
 * This is the ONLY file that changes when adding a new action: append one
 * line to the returned list, passing the services that handler needs.
 *
 * It is also the single place in the module that legitimately sees every
 * service at once — handlers themselves each hold only the narrow slice
 * they declared. The [dev.mcbookshelf.sniffer.dispatch.Dispatcher] is
 * built from this list in [dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher].
 */
fun buildHandlers(): List<Handler<*>> {
    val scopeManager = ScopeManager.get()
    val evaluationStore = EvaluationVariableStore

    return listOf(
        // Stepping / execution control
        StepOverHandler(),
        StepInHandler(),
        StepOutHandler(),
        ContinueHandler(),
        ResetSteppingHandler(),
        // Debug mode / breakpoint triggers
        SetDebugModeHandler(),
        TriggerBreakpointHandler(),
        // In-game command handlers
        GetVariableHandler(),
        GetAllVariablesHandler(),
        GetStackHandler(),
        // DAP handlers
        SetBreakpointsHandler(BreakpointManager),
        GetStackTraceHandler(scopeManager),
        GetScopesHandler(scopeManager),
        ResolveVariablesHandler(scopeManager, evaluationStore),
        EvaluateHandler(scopeManager, evaluationStore),
        GetSourceHandler(),
    )
}
