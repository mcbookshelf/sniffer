package dev.mcbookshelf.sniffer.network

import dev.mcbookshelf.sniffer.Constants.DAP_CONNECTED_PACKET_ID
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Clientbound payload that notifies players whether a DAP client is currently
 * attached to the Sniffer WebSocket server. The HUD overlay reads this value
 * to choose between the "connected" and "disconnected" status icons.
 *
 * Broadcast to every online player on every state change so any player with
 * debug mode enabled sees a live indicator, regardless of whether they were
 * the one who approved the attach.
 */
@JvmRecord
data class SetDapConnectedPayload(val connected: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SetDapConnectedPayload> =
            CustomPacketPayload.Type(DAP_CONNECTED_PACKET_ID)

        val CODEC: StreamCodec<FriendlyByteBuf, SetDapConnectedPayload> =
            StreamCodec.composite(
                ByteBufCodecs.BOOL, SetDapConnectedPayload::connected,
                { connected: Boolean? -> SetDapConnectedPayload(connected!!) }
            )
    }
}
