package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.output.VariableOutput
import dev.mcbookshelf.sniffer.input.GetVariableInput

/**
 * Retrieves a single variable by key from the current debug scope.
 *
 * Returns a [VariableOutput] with the variable value. If no scope
 * is active, returns a [VariableOutput] with null value.
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
