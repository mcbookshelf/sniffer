package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.EvaluationVariableStore
import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.ResolveVariablesInput
import dev.mcbookshelf.sniffer.output.ResolveVariablesOutput
import kotlin.math.min

/**
 * Resolves variables for a given reference.
 *
 * Variables can come from two sources:
 * - **Scope variables** (reference < threshold) — looked up in [ScopeManager]
 * - **Expression variables** (reference >= threshold) — looked up in [EvaluationVariableStore]
 *
 * Applies optional pagination via [ResolveVariablesInput.start] and
 * [ResolveVariablesInput.count].
 */
class ResolveVariablesHandler(
    private val scopeManager: ScopeManager,
    private val evaluationStore: EvaluationVariableStore,
) : Handler<ResolveVariablesInput> {

    override val inputType = ResolveVariablesInput::class

    override fun handle(input: ResolveVariablesInput, ctx: Context): Output {
        var variables = if (evaluationStore.isExpressionVariable(input.variablesReference)) {
            evaluationStore.getChildren(input.variablesReference)
        } else {
            scopeManager.getVariables(input.variablesReference).orElseGet { ArrayList() }
        }

        val start = (input.start ?: 0).coerceIn(0, variables.size)
        val count = input.count ?: variables.size
        val end = min(start + count, variables.size)
        variables = variables.subList(start, end)

        return ResolveVariablesOutput(variables)
    }
}
