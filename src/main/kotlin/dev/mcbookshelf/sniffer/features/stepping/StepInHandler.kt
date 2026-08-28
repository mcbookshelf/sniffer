package dev.mcbookshelf.sniffer.features.stepping


/**
 * Steps into the called functions.
 *
 * @author theogiraudet
 */
class StepInHandler : StepHandler<StepInInput>(StepType.STEP_IN) {
    override val inputType = StepInInput::class
}
