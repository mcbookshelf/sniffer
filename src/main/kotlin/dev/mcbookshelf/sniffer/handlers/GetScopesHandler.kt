package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.GetScopesInput
import dev.mcbookshelf.sniffer.output.ScopeData
import dev.mcbookshelf.sniffer.output.ScopesOutput

/**
 * Returns the variable scopes for a given stack frame.
 *
 * Currently every frame exposes a single "Function" scope containing
 * the command source variables and macro arguments (if any).
 */
class GetScopesHandler(
    private val scopeManager: ScopeManager,
) : Handler<GetScopesInput> {

    override val inputType = GetScopesInput::class

    override fun handle(input: GetScopesInput, ctx: Context): Output {
        if (scopeManager.isEmpty()) return ScopesOutput(listOf(emptyScopeData()))

        val scope = scopeManager.getScope(input.frameId).orElse(null)
            ?: return ScopesOutput(listOf(emptyScopeData()))

        val data = ScopeData(
            id = scope.id,
            name = "Function",
            variableCount = scope.rootVariables.size,
            functionName = scope.function,
            path = scope.path,
        )
        return ScopesOutput(listOf(data))
    }

    private fun emptyScopeData() = ScopeData(
        id = 0,
        name = "Function",
        variableCount = 0,
        functionName = "",
        path = null,
    )
}
