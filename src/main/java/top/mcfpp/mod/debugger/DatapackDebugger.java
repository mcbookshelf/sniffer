package top.mcfpp.mod.debugger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.glassfish.tyrus.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.mcfpp.mod.debugger.command.BreakPointCommand;
import top.mcfpp.mod.debugger.command.FunctionPathGetter;
import top.mcfpp.mod.debugger.dap.DapServer;
import top.mcfpp.mod.debugger.dap.DebuggerState;
import top.mcfpp.mod.debugger.dap.WebSocketServer;

/**
 * Main class of the Datapack Debugger mod.
 * This mod provides debugging capabilities for Minecraft datapacks by adding breakpoints
 * and debugging features to help developers debug their datapack functions.
 */
public class DatapackDebugger implements ModInitializer {
	/** Main logger for the mod's logging system */
	private static final Logger logger = LoggerFactory.getLogger("datapack-debugger");
	private static Server webSocketServer;

	/**
	 * Mod initialization method called on startup.
	 * Configures server events and initializes debugging commands.
	 */
	@Override
	public void onInitialize() {
		// Clear breakpoints when server starts
		ServerLifecycleEvents.SERVER_STARTED.register(server -> BreakPointCommand.clear());
		ServerLifecycleEvents.SERVER_STARTED.register(DebuggerState.get()::setServer);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> WebSocketServer.launch(25599).ifPresent(wss -> webSocketServer = wss));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> webSocketServer.stop());
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FunctionPathGetter());

		// Initialize breakpoint command system
		BreakPointCommand.onInitialize();
	}

	/**
	 * Retrieves the main logger of the mod.
	 * @return The logger used for mod event logging
	 */
	public static Logger getLogger(){
		return logger;
	}
}
