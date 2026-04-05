package dev.mcbookshelf.sniffer.accessor

import net.minecraft.server.packs.FilePackResources

interface FilePackResourcesAccessor {
    fun `sniffer$getZipFileAccess`(): FilePackResources.SharedZipFileAccess
    fun `sniffer$getPrefix`(): String
}
