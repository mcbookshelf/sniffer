package dev.mcbookshelf.sniffer.features.variables

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieves one macro argument by name.
 *
 * @property key name of the argument to look up
 * @author theogiraudet
 */
data class GetVariableInput(val key: String) : IInput
