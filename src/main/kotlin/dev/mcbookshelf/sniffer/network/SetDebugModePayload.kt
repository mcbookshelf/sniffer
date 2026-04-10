package dev.mcbookshelf.sniffer.network

import dev.mcbookshelf.sniffer.Constants.DEBUG_MODE_PACKET_ID
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Clientbound payload that pushes a player's debug-mode state to their client.
 *
 * Debug mode is a per-user, HUD-only toggle (see [dev.mcbookshelf.sniffer.state.DebugModeState]);
 * the server is the source of truth for persistence across reconnects, but the
 * client keeps its own local copy so the HUD can read it cheaply each frame.
 */
@JvmRecord
data class SetDebugModePayload(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SetDebugModePayload> =
            CustomPacketPayload.Type(DEBUG_MODE_PACKET_ID)

        val CODEC: StreamCodec<FriendlyByteBuf, SetDebugModePayload> =
            StreamCodec.composite(
                ByteBufCodecs.BOOL, SetDebugModePayload::enabled,
                { enabled: Boolean? -> SetDebugModePayload(enabled!!) }
            )
    }
}