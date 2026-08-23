package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.StepInput

/**
 * Advances by [lines] commands, entering the functions called along the way.
 *
 * @property lines how many commands to run, at least one
 * @author theogiraudet
 */
data class StepInInput(override val lines: Int = 1) : StepInput
