package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.EvaluationSession
import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.state.BreakpointManager

/**
 * Wires every [Handler] with the services it needs and returns the list the dispatcher is built from.
 * Adding an action means adding one line here, which is the only place seeing every service at once.
 */
fun buildHandlers(): List<Handler<*>> {
    val scopeManager = ScopeManager.get()
    val evaluationSession = EvaluationSession(scopeManager.registry)

    return listOf(
        StepOverHandler(),
        StepInHandler(),
        StepOutHandler(),
        ContinueHandler(),
        ResetSteppingHandler(),
        SetDebugModeHandler(),
        TriggerBreakpointHandler(BreakpointManager),
        GetVariableHandler(),
        GetAllVariablesHandler(),
        GetStackHandler(),
        SetBreakpointsHandler(BreakpointManager),
        GetStackTraceHandler(scopeManager),
        GetScopesHandler(scopeManager),
        ResolveVariablesHandler(scopeManager),
        EvaluateHandler(scopeManager, evaluationSession),
        GetSourceHandler(),
    )
}
