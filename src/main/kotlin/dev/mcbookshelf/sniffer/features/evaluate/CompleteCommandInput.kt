package dev.mcbookshelf.sniffer.features.evaluate

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Asks what could be typed next in a command.
 *
 * @property command the command as typed so far, leading slash optional
 * @property cursor how far into [command] the caret is, in characters from its start
 * @author theogiraudet
 */
data class CompleteCommandInput(val command: String, val cursor: Int) : IInput
