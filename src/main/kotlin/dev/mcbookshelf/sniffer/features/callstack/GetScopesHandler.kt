package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.source.FunctionIdentity

/**
 * Returns the variable scopes of a stack frame.
 * Every frame exposes a single `Function` scope, holding the command source variables and the macro arguments.
 *
 * @author theogiraudet
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
            variableCount = scopeManager.getVariables(scope.id).orElse(emptyList()).size,
            identity = scope.identity,
        )
        return ScopesOutput(listOf(data))
    }

    private fun emptyScopeData() = ScopeData(
        id = 0,
        name = "Function",
        variableCount = 0,
        identity = FunctionIdentity("", null),
    )
}
