package dev.mcbookshelf.sniffer.handlers

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.SetDebugModeInput
import dev.mcbookshelf.sniffer.network.SetDebugModePayload
import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.state.DebugModeState
import net.minecraft.server.level.ServerPlayer

/**
 * Updates the caller's HUD-only debug mode.
 *
 * Debug mode is per-player (see [DebugModeState]) and never gates any
 * debugging capability — it only controls which icons the player's HUD
 * renders. This handler writes the new value to the server-side map and
 * pushes a [SetDebugModePayload] to that player so their client mirror
 * stays in sync. Non-player sources (console, command blocks) are a no-op
 * since there is no HUD to update.
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