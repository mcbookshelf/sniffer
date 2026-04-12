package dev.mcbookshelf.sniffer.network

import dev.mcbookshelf.sniffer.Constants.DEBUGGING_PACKET_ID
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Clientbound payload mirroring [dev.mcbookshelf.sniffer.state.SteppingState.isDebugging]
 * to each client so the HUD can show or hide the bug overlay when execution
 * is paused on a breakpoint or during stepping.
 */
@JvmRecord
data class SetDebuggingPayload(val debugging: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SetDebuggingPayload> =
            CustomPacketPayload.Type(DEBUGGING_PACKET_ID)

        val CODEC: StreamCodec<FriendlyByteBuf, SetDebuggingPayload> =
            StreamCodec.composite(
                ByteBufCodecs.BOOL, SetDebuggingPayload::debugging,
                { debugging: Boolean? -> SetDebuggingPayload(debugging!!) }
            )
    }
}
