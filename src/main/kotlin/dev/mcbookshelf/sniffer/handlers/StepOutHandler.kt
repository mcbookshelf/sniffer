package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.StepType
import dev.mcbookshelf.sniffer.input.StepOutInput

/**
 * Resumes until the running function returns, then pauses.
 *
 * @author theogiraudet
 */
class StepOutHandler : StepHandler<StepOutInput>(StepType.STEP_OUT) {
    override val inputType = StepOutInput::class
}
