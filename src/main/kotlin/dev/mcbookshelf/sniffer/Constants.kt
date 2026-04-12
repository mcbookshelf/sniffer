package dev.mcbookshelf.sniffer

import net.minecraft.resources.Identifier

object Constants {

    @JvmField
    val DEBUG_MODE_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath("sniffer", "debug_mode_sync")

    @JvmField
    val AUTH_PROMPT_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath("sniffer", "auth_prompt")

    @JvmField
    val AUTH_RESPONSE_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath("sniffer", "auth_response")

    @JvmField
    val DAP_CONNECTED_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath("sniffer", "dap_connected")

    @JvmField
    val DEBUGGING_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath("sniffer", "debugging")
}