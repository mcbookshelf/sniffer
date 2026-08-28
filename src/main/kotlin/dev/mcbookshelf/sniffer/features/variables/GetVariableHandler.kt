package dev.mcbookshelf.sniffer.features.variables

import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Retrieves one macro argument of the current scope by name.
 * The returned value is empty when there is no scope, no macro, or no argument under that name.
 *
 * @author theogiraudet
 */
class GetVariableHandler : Handler<GetVariableInput> {

    override val inputType = GetVariableInput::class

    override fun handle(input: GetVariableInput, ctx: Context): Output {
        val scope = ScopeManager.get().currentScope.orElse(null)
            ?: return VariableOutput(key = input.key)
        val macroVars = scope.macroVariables
            ?: return VariableOutput(key = input.key, isMacro = false)
        val tag = macroVars.get(input.key)
            ?: return VariableOutput(key = input.key, isMacro = true)
        return VariableOutput(key = input.key, value = tag, isMacro = true)
    }
}
