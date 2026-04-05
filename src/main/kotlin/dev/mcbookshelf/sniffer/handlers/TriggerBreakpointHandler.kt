package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.TriggerBreakpointInput
import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.state.BreakpointTrigger

/**
 * Triggers a breakpoint at the current execution position.
 *
 * Delegates to [BreakpointTrigger.trigger] which freezes the
 * tick-rate manager, notifies DAP stop consumers, and enables debugging mode.
 */
class TriggerBreakpointHandler : Handler<TriggerBreakpointInput> {

    override val inputType = TriggerBreakpointInput::class

    override fun handle(input: TriggerBreakpointInput, ctx: Context): Output {
        BreakpointTrigger.trigger(ctx.source)
        return Ack
    }
}
