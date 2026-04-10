package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieve the source text of a function by its Minecraft identifier.
 *
 * @property functionId the Minecraft identifier string (e.g. "namespace:path").
 */
data class GetSourceInput(val functionId: String) : IInput
