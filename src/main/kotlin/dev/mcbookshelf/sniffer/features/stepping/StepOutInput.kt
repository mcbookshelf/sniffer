package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.StepInput

/**
 * Resumes until the running function returns, then pauses again.
 *
 * @property lines how many commands to run, at least one
 * @author theogiraudet
 */
data class StepOutInput(override val lines: Int = 1) : StepInput
