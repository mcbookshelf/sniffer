package dev.mcbookshelf.sniffer

import net.minecraft.resources.Identifier

object Constants {

    @JvmField
    val DEBUG_MODE_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath("sniffer", "debug_mode_sync")
}