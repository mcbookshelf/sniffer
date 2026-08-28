package dev.mcbookshelf.sniffer.features.debugmode

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.network.SetDebugModePayload
import dev.mcbookshelf.sniffer.dispatch.Ack
import net.minecraft.server.level.ServerPlayer

/**
 * Turns the HUD overlay of the calling player on or off.
 *
 * The new value is written to [DebugModeState] and pushed to that player, so their client mirror follows.
 * A source that is not a player has no HUD, and is ignored.
 *
 * @author theogiraudet
 */
class SetDebugModeHandler : Handler<SetDebugModeInput> {

    override val inputType = SetDebugModeInput::class

    override fun handle(input: SetDebugModeInput, ctx: Context): Output {
        val player = ctx.source.entity as? ServerPlayer ?: return Ack
        DebugModeState.setEnabled(player.uuid, input.enabled)
        ServerPlayNetworking.send(player, SetDebugModePayload(input.enabled))
        return Ack
    }
}