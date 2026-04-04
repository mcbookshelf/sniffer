package net.gunivers.sniffer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import org.lwjgl.glfw.GLFW;
import net.gunivers.sniffer.command.BreakPointCommand;

/**
 * @author Alumopper
 * @author theogiraudet
 */
public class DatapackBreakpointClient implements ClientModInitializer {

	private static KeyMapping stepInto;

	@Override
	public void onInitializeClient() {
		stepInto = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"sniffer.step", // The translation key of the keybinding's name
				InputConstants.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
				GLFW.GLFW_KEY_F7, // The keycode of the key
				KeyMapping.Category.register(ResourceLocation.parse("sniffer.name")) // The translation key of the keybinding's category.
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while(stepInto.isDown()) {
				if(BreakPointCommand.debugMode) {
                    assert client.player != null;
                    assert client.getSingleplayerServer() != null;
                    BreakPointCommand.step(1, client.player.createCommandSourceStackForNameResolution(client.getSingleplayerServer().getLevel(client.player.level().dimension())));
				}
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if(Minecraft.getInstance().hasSingleplayerServer() && DatapackDebugger.getWebSocketServer() != null){
				var port = DatapackDebugger.getWebSocketServer().getPort();
                assert client.player != null;
                client.player.displayClientMessage(
						Component.literal("DAP Server is running on port: ")
								.append(Component.literal("[" + port + "]").withStyle(style ->
												style.withColor(CommonColors.GREEN)
														.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy")))
														.withClickEvent(new ClickEvent.CopyToClipboard(String.valueOf(port)))
										)
								),
						false
				);
			}
		});
	}

}
