package dev.mcbookshelf.sniffer.client.auth

import dev.mcbookshelf.sniffer.network.AuthResponsePayload
import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.ButtonComponent
import io.wispforest.owo.ui.component.UIComponents
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.container.UIContainers
import io.wispforest.owo.ui.core.HorizontalAlignment
import io.wispforest.owo.ui.core.Insets
import io.wispforest.owo.ui.core.OwoUIAdapter
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.core.Surface
import io.wispforest.owo.ui.core.VerticalAlignment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * Owo-lib modal that asks the player to accept or reject an incoming
 * Debug Adapter Protocol attach attempt impersonating their account.
 */
class AuthPromptScreen(
    private val requestId: UUID,
    private val clientDescription: String,
    timeoutSeconds: Int,
) : BaseOwoScreen<FlowLayout>(Component.translatable("sniffer.auth.title")) {

    private var decisionSent = false
    private val deadlineMs: Long = System.currentTimeMillis() + timeoutSeconds * 1000L
    private var rejectButton: ButtonComponent? = null

    override fun createAdapter(): OwoUIAdapter<FlowLayout> =
        OwoUIAdapter.create(this, UIContainers::verticalFlow)

    override fun build(rootComponent: FlowLayout) {
        rootComponent
            .surface(Surface.VANILLA_TRANSLUCENT)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)

        val panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content())
            .gap(8)
            .padding(Insets.of(16))
            .surface(Surface.DARK_PANEL)
            .horizontalAlignment(HorizontalAlignment.CENTER) as FlowLayout

        panel.child(UIComponents.label(Component.translatable("sniffer.auth.title")))
        panel.child(UIComponents.label(Component.translatable("sniffer.auth.body")))
        panel.child(UIComponents.label(Component.literal(clientDescription)))

        val buttons = UIContainers.horizontalFlow(Sizing.content(), Sizing.content())
            .gap(8) as FlowLayout
        buttons.child(
            UIComponents.button(Component.translatable("sniffer.auth.accept")) { _ -> respond(true) }
                .horizontalSizing(Sizing.fixed(80))
        )
        rejectButton = UIComponents.button(Component.translatable("sniffer.auth.reject", remainingSeconds())) { _ -> respond(false) }
            .horizontalSizing(Sizing.fixed(80)) as ButtonComponent
        buttons.child(rejectButton!!)
        panel.child(buttons)

        rootComponent.child(panel)
    }

    override fun tick() {
        super.tick()
        val remaining = remainingSeconds()
        rejectButton?.message = Component.translatable("sniffer.auth.reject", remaining)
        if (remaining <= 0 && !decisionSent) {
            Minecraft.getInstance().setScreen(null)
        }
    }

    private fun remainingSeconds(): Int {
        val remainingMs = deadlineMs - System.currentTimeMillis()
        return if (remainingMs <= 0) 0 else ((remainingMs + 999) / 1000).toInt()
    }

    private fun respond(accepted: Boolean) {
        if (decisionSent) return
        decisionSent = true
        ClientPlayNetworking.send(AuthResponsePayload(requestId, accepted))
        Minecraft.getInstance().setScreen(null)
    }

    override fun shouldCloseOnEsc(): Boolean = true

    override fun removed() {
        // Treat any close-without-explicit-decision as a rejection.
        if (!decisionSent) {
            decisionSent = true
            try {
                ClientPlayNetworking.send(AuthResponsePayload(requestId, false))
            } catch (_: Exception) {
                // Best-effort: client might already be tearing down the connection.
            }
        }
        super.removed()
    }
}
