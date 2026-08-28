package dev.mcbookshelf.sniffer.features.stepping


/**
 * Steps over the called functions, running each of them as a single step.
 *
 * @author theogiraudet
 */
class StepOverHandler : StepHandler<StepOverInput>(StepType.STEP_OVER) {
    override val inputType = StepOverInput::class
}
