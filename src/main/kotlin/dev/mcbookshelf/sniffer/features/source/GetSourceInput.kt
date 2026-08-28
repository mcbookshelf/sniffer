package dev.mcbookshelf.sniffer.features.source

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieves the source text of a function.
 *
 * @property functionId location of the function, as `namespace:path`
 * @author theogiraudet
 */
data class GetSourceInput(val functionId: String) : IInput
