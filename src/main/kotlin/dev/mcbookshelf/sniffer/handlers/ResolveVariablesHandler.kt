package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.ResolveVariablesInput
import dev.mcbookshelf.sniffer.output.ResolveVariablesOutput
import kotlin.math.min

/**
 * Resolves the variables a reference points to, paginating them when the request asks for it.
 *
 * Scope roots and nested variables live in the same registry, so this is a single lookup,
 * and the children of the node are built on this first request.
 *
 * @author theogiraudet
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
