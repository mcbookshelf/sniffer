package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.StepInput

/**
 * Resume until the current function frame returns, then pause again.
 *
 * @property lines number of commands to step; must be >= 1. Defaults to 1.
 */
data class StepOutInput(override val lines: Int = 1) : StepInput
