package dev.mcbookshelf.sniffer.accessor

import net.minecraft.server.packs.FilePackResources

/**
 * Exposes the zip file and the prefix of a pack, so a function can be traced back to it.
 *
 * @author theogiraudet
 */
interface FilePackResourcesAccessor {
    fun `sniffer$getZipFileAccess`(): FilePackResources.SharedZipFileAccess
    fun `sniffer$getPrefix`(): String
}
