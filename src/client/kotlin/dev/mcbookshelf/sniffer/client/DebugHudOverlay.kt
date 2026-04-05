package dev.mcbookshelf.sniffer.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import dev.mcbookshelf.sniffer.state.ConnectionState
import dev.mcbookshelf.sniffer.state.DebugToggles
import dev.mcbookshelf.sniffer.state.SteppingState
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/**
 * Top-right HUD element that renders Sniffer's debug icons.
 *
 * Debug mode is the per-user gate: when it is off, nothing is drawn at all.
 * When it is on, the overlay always shows a DAP connection status icon
 * ([CONNECTED_ICON] or [DISCONNECTED_ICON]) and additionally overlays the
 * [BUG_ICON] whenever active debugging is in progress — i.e. the server
 * thread is currently paused on a breakpoint or stepping.
 */
class DebugHudOverlay : HudElement {

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!DebugToggles.debugMode) return

        val screenWidth = graphics.guiWidth()
        val x = screenWidth - ICON_SIZE - MARGIN
        val y = MARGIN

        val statusIcon = if (ConnectionState.clientConnected) CONNECTED_ICON else DISCONNECTED_ICON
        graphics.blit(RenderPipelines.GUI_TEXTURED, statusIcon, x, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE)

        if (SteppingState.isDebugging) {
            val bugX = x - ICON_SIZE - MARGIN
            graphics.blit(RenderPipelines.GUI_TEXTURED, BUG_ICON, bugX, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE)
        }
    }

    companion object {
        private val BUG_ICON = Identifier.parse("sniffer:textures/gui/bug_icon.png")
        private val CONNECTED_ICON = Identifier.parse("sniffer:textures/gui/connected_icon.png")
        private val DISCONNECTED_ICON = Identifier.parse("sniffer:textures/gui/disconnected_icon.png")
        private const val ICON_SIZE = 16
        private const val MARGIN = 5
    }
}
