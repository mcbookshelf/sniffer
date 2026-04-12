package dev.mcbookshelf.sniffer.network

import dev.mcbookshelf.sniffer.Constants.AUTH_PROMPT_PACKET_ID
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/**
 * Clientbound payload sent to a player whose username was declared by an
 * incoming DAP WebSocket connection. Triggers the in-game accept/reject prompt.
 */
@JvmRecord
data class AuthPromptPayload(
    val requestId: UUID,
    val clientDescription: String,
    val timeoutSeconds: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<AuthPromptPayload> =
            CustomPacketPayload.Type(AUTH_PROMPT_PACKET_ID)

        val CODEC: StreamCodec<FriendlyByteBuf, AuthPromptPayload> =
            StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, AuthPromptPayload::requestId,
                ByteBufCodecs.STRING_UTF8, AuthPromptPayload::clientDescription,
                ByteBufCodecs.VAR_INT, AuthPromptPayload::timeoutSeconds,
                ::AuthPromptPayload
            )
    }
}
