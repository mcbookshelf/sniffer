package dev.mcbookshelf.sniffer.accessor

import java.nio.file.Path

interface PathPackResourcesAccessor {
    fun `sniffer$getRoot`(): Path
}
