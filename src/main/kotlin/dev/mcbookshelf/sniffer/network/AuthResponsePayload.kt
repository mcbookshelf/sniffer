package dev.mcbookshelf.sniffer.network

import dev.mcbookshelf.sniffer.Constants.AUTH_RESPONSE_PACKET_ID
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/**
 * Serverbound payload carrying the player's accept/reject decision for a
 * pending DAP authentication prompt.
 */
@JvmRecord
data class AuthResponsePayload(val requestId: UUID, val accepted: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<AuthResponsePayload> =
            CustomPacketPayload.Type(AUTH_RESPONSE_PACKET_ID)

        val CODEC: StreamCodec<FriendlyByteBuf, AuthResponsePayload> =
            StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, AuthResponsePayload::requestId,
                ByteBufCodecs.BOOL, AuthResponsePayload::accepted,
                ::AuthResponsePayload
            )
    }
}
