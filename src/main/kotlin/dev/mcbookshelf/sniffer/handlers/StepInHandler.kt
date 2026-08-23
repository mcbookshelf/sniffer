package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.StepType
import dev.mcbookshelf.sniffer.input.StepInInput

/**
 * Steps into the called functions.
 *
 * @author theogiraudet
 */
class StepInHandler : StepHandler<StepInInput>(StepType.STEP_IN) {
    override val inputType = StepInInput::class
}
