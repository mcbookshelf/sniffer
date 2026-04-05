package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.StepType
import dev.mcbookshelf.sniffer.input.StepOverInput

/** Steps over nested function calls (treats them as a single step). */
class StepOverHandler : StepHandler<StepOverInput>(StepType.STEP_OVER) {
    override val inputType = StepOverInput::class
}
