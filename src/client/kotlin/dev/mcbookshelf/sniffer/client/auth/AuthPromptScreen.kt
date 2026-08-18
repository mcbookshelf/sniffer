package dev.mcbookshelf.sniffer.client.auth

import dev.mcbookshelf.sniffer.network.AuthResponsePayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * Modal that asks the player to accept or reject an incoming Debug Adapter
 * Protocol attach attempt impersonating their account.
 */
class AuthPromptScreen(
    private val requestId: UUID,
    private val clientDescription: String,
    timeoutSeconds: Int,
) : Screen(Component.translatable("sniffer.auth.title")) {

    private var decisionSent = false
    private val deadlineMs: Long = System.currentTimeMillis() + timeoutSeconds * 1000L
    private val layout = LinearLayout.vertical().spacing(8)
    private var rejectButton: Button? = null

    override fun init() {
        super.init()
        layout.defaultCellSetting().alignHorizontallyCenter()
        layout.addChild(StringWidget(title, font))
        layout.addChild(
            MultiLineTextWidget(Component.translatable("sniffer.auth.body"), font)
                .setMaxWidth(width - 50)
                .setCentered(true)
        )
        layout.addChild(
            MultiLineTextWidget(Component.literal(clientDescription), font)
                .setMaxWidth(width - 50)
                .setCentered(true)
        )

        val buttons = layout.addChild(LinearLayout.horizontal().spacing(8))
        buttons.defaultCellSetting().paddingTop(16)
        buttons.addChild(
            Button.builder(Component.translatable("sniffer.auth.accept")) { respond(true) }
                .width(80)
                .build()
        )
        rejectButton = buttons.addChild(
            Button.builder(Component.translatable("sniffer.auth.reject", remainingSeconds())) { respond(false) }
                .width(80)
                .build()
        )

        layout.visitWidgets(this::addRenderableWidget)
        repositionElements()
    }

    override fun repositionElements() {
        layout.arrangeElements()
        FrameLayout.centerInRectangle(layout, rectangle)
    }

    override fun tick() {
        super.tick()
        val remaining = remainingSeconds()
        rejectButton?.message = Component.translatable("sniffer.auth.reject", remaining)
        if (remaining <= 0 && !decisionSent) {
            Minecraft.getInstance().gui.setScreen(null)
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
        Minecraft.getInstance().gui.setScreen(null)
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
