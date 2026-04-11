package dev.mcbookshelf.sniffer.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import dev.mcbookshelf.sniffer.Sniffer
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.input.StepInInput
import dev.mcbookshelf.sniffer.network.SetDebugModePayload
import dev.mcbookshelf.sniffer.state.DebugToggles
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.Identifier
import net.minecraft.util.CommonColors
import org.lwjgl.glfw.GLFW


/**
 * @author Alumopper
 * @author theogiraudet
 */
class SnifferClient : ClientModInitializer {

    override fun onInitializeClient() {
        HudElementRegistry.addLast(Identifier.parse("sniffer:debug_icon"), DebugHudOverlay())

        ClientPlayNetworking.registerGlobalReceiver(SetDebugModePayload.TYPE) { payload, _ ->
            DebugToggles.debugMode = payload.enabled
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            DebugToggles.debugMode = false
        }

        val stepInto = KeyMappingHelper.registerKeyMapping(KeyMapping(
            "sniffer.step",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KeyMapping.Category.register(Identifier.parse("sniffer.name"))
        ))

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (stepInto.isDown) {
                val server = client.singleplayerServer ?: continue
                val player = client.player ?: continue
                val level = server.getLevel(player.level().dimension()) ?: continue
                val source = player.createCommandSourceStackForNameResolution(level)
                SnifferDispatcher.get().dispatch(StepInInput(1), Context(source, server))
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            if (Minecraft.getInstance().hasSingleplayerServer()) {
                val port = Sniffer.webSocketServer?.port ?: return@register
                val player = client.player ?: return@register
                player.sendSystemMessage(
                    Component.literal("Sniffer Server is running on port: ")
                        .append(
                            Component.literal("[$port]").withStyle { style ->
                                style.withColor(CommonColors.GREEN)
                                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Click to copy")))
                                    .withClickEvent(ClickEvent.CopyToClipboard(port.toString()))
                            }
                        )
                )
            }
        }
    }
}
