package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.PausedExecutionStore
import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.state.StepType
import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.StepInput
import dev.mcbookshelf.sniffer.state.SteppingState
import net.minecraft.network.chat.Component

/**
 * Base handler for all step actions (step-in, step-over, step-out).
 *
 * Each concrete subclass declares its [inputType] and stepping [policy].
 * The handler sets [SteppingState] fields and asks
 * [PausedExecutionStore] to replay the suspended execution on the next
 * server tick. The mixin ([dev.mcbookshelf.sniffer.mixin.UnboundDebugMixin])
 * reads the stepping fields to decide when to re-pause.
 *
 * @param I the concrete [StepInput] subtype this handler routes.
 * @property policy the [StepType] applied when this handler runs.
 */
abstract class StepHandler<I : StepInput>(
    private val policy: StepType,
) : Handler<I> {

    override fun handle(input: I, ctx: Context): Output {
        if (!SteppingState.isDebugging) {
            ctx.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.step.fail"))
            return Ack
        }

        SteppingState.stepType = policy
        SteppingState.stepsRemaining = input.lines
        SteppingState.stepDepth = ScopeManager.get().count()

        PausedExecutionStore.scheduleResume(ctx.source.server)

        return Ack
    }
}
