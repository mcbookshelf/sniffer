package dev.mcbookshelf.sniffer.dispatch

import dev.mcbookshelf.sniffer.features.evaluate.EvaluationSession
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.features.breakpoints.BreakpointManager
import dev.mcbookshelf.sniffer.features.breakpoints.SetBreakpointsHandler
import dev.mcbookshelf.sniffer.features.breakpoints.TriggerBreakpointHandler
import dev.mcbookshelf.sniffer.features.callstack.ClearScopesHandler
import dev.mcbookshelf.sniffer.features.callstack.GetScopesHandler
import dev.mcbookshelf.sniffer.features.callstack.GetStackHandler
import dev.mcbookshelf.sniffer.features.callstack.GetStackTraceHandler
import dev.mcbookshelf.sniffer.features.debugmode.SetDebugModeHandler
import dev.mcbookshelf.sniffer.features.evaluate.CompleteCommandHandler
import dev.mcbookshelf.sniffer.features.evaluate.EvaluateHandler
import dev.mcbookshelf.sniffer.features.evaluate.RunCommandHandler
import dev.mcbookshelf.sniffer.features.source.GetSourceHandler
import dev.mcbookshelf.sniffer.features.stepping.ContinueHandler
import dev.mcbookshelf.sniffer.features.stepping.PauseHandler
import dev.mcbookshelf.sniffer.features.stepping.ResetSteppingHandler
import dev.mcbookshelf.sniffer.features.stepping.StepInHandler
import dev.mcbookshelf.sniffer.features.stepping.StepOutHandler
import dev.mcbookshelf.sniffer.features.stepping.StepOverHandler
import dev.mcbookshelf.sniffer.features.variables.GetAllVariablesHandler
import dev.mcbookshelf.sniffer.features.variables.GetVariableHandler
import dev.mcbookshelf.sniffer.features.variables.ResolveVariablesHandler
import dev.mcbookshelf.sniffer.dap.DapClient
import dev.mcbookshelf.sniffer.features.trace.TraceClient
import dev.mcbookshelf.sniffer.features.trace.TraceHandler

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
        PauseHandler(),
        ResetSteppingHandler(),
        SetDebugModeHandler(),
        TriggerBreakpointHandler(),
        GetVariableHandler(),
        GetAllVariablesHandler(),
        GetStackHandler(),
        SetBreakpointsHandler(BreakpointManager),
        GetStackTraceHandler(scopeManager),
        GetScopesHandler(scopeManager),
        ClearScopesHandler(scopeManager),
        ResolveVariablesHandler(scopeManager),
        EvaluateHandler(scopeManager, evaluationSession),
        RunCommandHandler(scopeManager),
        CompleteCommandHandler(scopeManager),
        GetSourceHandler(),
        TraceHandler { DapClient.of(TraceClient::class.java) },
    )
}
