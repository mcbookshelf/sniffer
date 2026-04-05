package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.output.AllVariablesOutput
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.GetAllVariablesInput

/**
 * Retrieves all variables from the current debug scope.
 *
 * Returns an [AllVariablesOutput] with the scope variables, or null
 * if no scope is active.
 */
class GetAllVariablesHandler : Handler<GetAllVariablesInput> {

    override val inputType = GetAllVariablesInput::class

    override fun handle(input: GetAllVariablesInput, ctx: Context): Output {
        val scope = ScopeManager.get().currentScope.orElse(null)
            ?: return AllVariablesOutput()
        val macroVars = scope.macroVariables
            ?: return AllVariablesOutput()
        return AllVariablesOutput(value = macroVars)
    }
}
