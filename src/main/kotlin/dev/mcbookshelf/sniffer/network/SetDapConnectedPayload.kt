package dev.mcbookshelf.sniffer.network

import dev.mcbookshelf.sniffer.Constants.DAP_CONNECTED_PACKET_ID
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Tells the clients whether a DAP client is attached, which the HUD turns into its status icon.
 * Broadcast to everyone on each change, so a player sees the indicator even if someone else approved the connection.
 *
 * @property connected whether a client is attached
 * @author theogiraudet
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
