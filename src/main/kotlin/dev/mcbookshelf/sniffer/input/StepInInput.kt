package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.StepInput

/**
 * Advance the paused thread by [lines] commands, descending into nested
 * function calls.
 *
 * @property lines number of commands to step; must be >= 1. Defaults to 1.
 */
data class StepInInput(override val lines: Int = 1) : StepInput
