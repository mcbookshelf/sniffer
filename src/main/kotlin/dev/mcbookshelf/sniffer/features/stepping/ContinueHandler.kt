package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dap.DebugEventBus
import dev.mcbookshelf.sniffer.chat.SnifferChat

/**
 * Resumes execution until the next breakpoint.
 * It clears the stepping state, tells the DAP client, and has the suspended execution replayed on the next tick.
 *
 * @author theogiraudet
 */
class ContinueHandler : Handler<ContinueInput> {

    override val inputType = ContinueInput::class

    override fun handle(input: ContinueInput, ctx: Context): Output {
        if (!SteppingState.isDebugging) {
            SnifferChat.fail(ctx.source, "sniffer.commands.breakpoint.move.not_debugging")
            return Ack
        }

        SteppingState.setDebugging(false)
        SteppingState.stepsRemaining = 0

        DebugEventBus.fireContinue()

        PausedExecutionStore.scheduleResume(ctx.source.server)

        return Ack
    }
}
