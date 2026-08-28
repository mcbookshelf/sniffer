package dev.mcbookshelf.sniffer.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import dev.mcbookshelf.sniffer.client.state.ClientConnectionState
import dev.mcbookshelf.sniffer.client.state.ClientDebuggingState
import dev.mcbookshelf.sniffer.features.debugmode.DebugToggles
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/**
 * Draws the debug icons in the top right corner, and nothing at all while debug mode is off.
 * A status icon says whether a DAP client is attached, and the bug icon is added on top while an execution is paused.
 *
 * @author theogiraudet
 */
class DebugHudOverlay : HudElement {

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!DebugToggles.debugMode) return

        val screenWidth = graphics.guiWidth()
        val x = screenWidth - ICON_SIZE - MARGIN
        val y = MARGIN

        val statusIcon = if (ClientConnectionState.connected) CONNECTED_ICON else DISCONNECTED_ICON
        graphics.blit(RenderPipelines.GUI_TEXTURED, statusIcon, x, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE)

        if (ClientDebuggingState.debugging) {
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
