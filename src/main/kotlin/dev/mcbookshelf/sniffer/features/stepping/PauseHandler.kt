package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.Ack
import dev.mcbookshelf.sniffer.chat.SnifferChat

/**
 * Arms [SteppingState.pauseRequested], which `UnboundDebugMixin` consumes on the next line it checks.
 * Already being paused is reported back instead, since there is nothing left to stop.
 *
 * @author theogiraudet
 */
class PauseHandler : Handler<PauseInput> {

    override val inputType = PauseInput::class

    override fun handle(input: PauseInput, ctx: Context): Output {
        if (SteppingState.isDebugging) {
            SnifferChat.fail(ctx.source, "sniffer.commands.breakpoint.pause.already")
            return Ack
        }

        SteppingState.pauseRequested = true
        return Ack
    }
}
