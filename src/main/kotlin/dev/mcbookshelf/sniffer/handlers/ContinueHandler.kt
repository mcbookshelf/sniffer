package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.ContinueInput
import dev.mcbookshelf.sniffer.state.DebugEventBus
import dev.mcbookshelf.sniffer.state.PausedExecutionStore
import dev.mcbookshelf.sniffer.state.SteppingState
import net.minecraft.network.chat.Component

/**
 * Resumes execution from the current pause point until the next breakpoint.
 *
 * Clears stepping state, notifies DAP clients via [DebugEventBus], and
 * asks [PausedExecutionStore] to replay the suspended execution on the
 * next server tick.
 */
class ContinueHandler : Handler<ContinueInput> {

    override val inputType = ContinueInput::class

    override fun handle(input: ContinueInput, ctx: Context): Output {
        if (!SteppingState.isDebugging) {
            ctx.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.move.not_debugging"))
            return Ack
        }

        SteppingState.setDebugging(false)
        SteppingState.stepsRemaining = 0

        DebugEventBus.fireContinue()

        PausedExecutionStore.scheduleResume(ctx.source.server)

        return Ack
    }
}
