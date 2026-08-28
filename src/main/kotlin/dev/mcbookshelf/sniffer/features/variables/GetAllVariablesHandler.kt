package dev.mcbookshelf.sniffer.features.variables

import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Retrieves every macro argument of the current scope, or nothing when there is no scope or no macro.
 *
 * @author theogiraudet
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
