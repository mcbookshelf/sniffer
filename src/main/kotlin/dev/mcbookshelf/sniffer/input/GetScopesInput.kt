package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieves the variable scopes of a stack frame.
 *
 * @property frameId id of the frame to look up
 * @author theogiraudet
 */
data class GetScopesInput(val frameId: Int) : IInput
