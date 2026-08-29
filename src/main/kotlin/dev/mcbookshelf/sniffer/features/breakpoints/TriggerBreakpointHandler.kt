package dev.mcbookshelf.sniffer.features.breakpoints

import dev.mcbookshelf.sniffer.dispatch.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Halts the execution at the current position, which is what `/breakpoint` does.
 *
 * @author theogiraudet
 */
class TriggerBreakpointHandler : Handler<TriggerBreakpointInput> {

    override val inputType = TriggerBreakpointInput::class

    override fun handle(input: TriggerBreakpointInput, ctx: Context): Output {
        BreakpointTrigger.trigger(ctx.source)
        return Ack
    }
}
