package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.dispatch.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Throws the call hierarchy away, leaving breakpoints and stepping counters alone.
 * The observers of the control flow are told, which is what ends a trace whose editor has gone.
 *
 * @author theogiraudet
 */
class ClearScopesHandler(private val scopeManager: ScopeManager) : Handler<ClearScopesInput> {

    override val inputType = ClearScopesInput::class

    override fun handle(input: ClearScopesInput, ctx: Context): Output {
        scopeManager.clear()
        return Ack
    }
}
