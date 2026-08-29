package dev.mcbookshelf.sniffer.features.evaluate

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Runs a command in the current scope and reports what it answered.
 *
 * @property command the command to run, leading slash optional
 * @author theogiraudet
 */
data class RunCommandInput(val command: String) : IInput
