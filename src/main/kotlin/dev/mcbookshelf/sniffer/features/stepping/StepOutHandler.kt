package dev.mcbookshelf.sniffer.features.stepping


/**
 * Resumes until the running function returns, then pauses.
 *
 * @author theogiraudet
 */
class StepOutHandler : StepHandler<StepOutInput>(StepType.STEP_OUT) {
    override val inputType = StepOutInput::class
}
