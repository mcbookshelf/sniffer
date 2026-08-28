package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.StepInput

/**
 * Advances by [lines] commands, running the functions called along the way without entering them.
 *
 * @property lines how many commands to run, at least one, and always one over DAP, which never batches a step
 * @author theogiraudet
 */
data class StepOverInput(override val lines: Int = 1) : StepInput
