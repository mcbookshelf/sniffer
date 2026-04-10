package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.ResetSteppingInput
import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.state.SteppingState

/**
 * Clears stepping counters, resets the execution lock, and restores
 * the debug toggle — preserving breakpoints and scopes.
 */
class ResetSteppingHandler : Handler<ResetSteppingInput> {

    override val inputType = ResetSteppingInput::class

    override fun handle(input: ResetSteppingInput, ctx: Context): Output {
        SteppingState.resetAll()
        return Ack
    }
}
