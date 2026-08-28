package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.Ack

/**
 * Clears the stepping state and drops any paused execution, leaving breakpoints and scopes alone.
 *
 * @author theogiraudet
 */
class ResetSteppingHandler : Handler<ResetSteppingInput> {

    override val inputType = ResetSteppingInput::class

    override fun handle(input: ResetSteppingInput, ctx: Context): Output {
        SteppingState.resetAll()
        return Ack
    }
}
