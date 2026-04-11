package dev.mcbookshelf.sniffer.handlers

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
 * Scope roots and nested variables share one [dev.mcbookshelf.sniffer.state.VariableRegistry],
 * so resolution is a single lookup. Children are materialized lazily by
 * [dev.mcbookshelf.sniffer.state.VariableNode.children] on first request.
 *
 * Applies optional pagination via [ResolveVariablesInput.start] and
 * [ResolveVariablesInput.count].
 */
class ResolveVariablesHandler(
    private val scopeManager: ScopeManager,
) : Handler<ResolveVariablesInput> {

    override val inputType = ResolveVariablesInput::class

    override fun handle(input: ResolveVariablesInput, ctx: Context): Output {
        var variables = scopeManager.getVariables(input.variablesReference).orElseGet { emptyList() }

        val start = (input.start ?: 0).coerceIn(0, variables.size)
        val count = input.count ?: variables.size
        val end = min(start + count, variables.size)
        variables = variables.subList(start, end)

        return ResolveVariablesOutput(variables)
    }
}
