package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.PauseInput
import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.state.SteppingState
import net.minecraft.network.chat.Component

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
            ctx.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.pause.already"))
            return Ack
        }

        SteppingState.pauseRequested = true
        return Ack
    }
}
