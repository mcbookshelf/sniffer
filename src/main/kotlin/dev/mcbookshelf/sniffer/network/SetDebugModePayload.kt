package dev.mcbookshelf.sniffer.network

import dev.mcbookshelf.sniffer.Constants.DEBUG_MODE_PACKET_ID
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Pushes the debug mode of a player to their own client, which keeps a copy the HUD can read every frame.
 *
 * @property enabled whether that player has the overlay on
 * @author theogiraudet
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