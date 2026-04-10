package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieve the variable scopes for a given stack frame.
 *
 * @property frameId the scope/frame ID to look up.
 */
data class GetScopesInput(val frameId: Int) : IInput
