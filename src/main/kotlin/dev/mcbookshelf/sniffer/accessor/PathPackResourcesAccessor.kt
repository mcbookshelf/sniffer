package dev.mcbookshelf.sniffer.accessor

import java.nio.file.Path

/**
 * Exposes the root directory of a pack, so a function can be traced back to it.
 *
 * @author theogiraudet
 */
interface PathPackResourcesAccessor {
    fun `sniffer$getRoot`(): Path
}
