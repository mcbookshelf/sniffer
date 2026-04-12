package dev.mcbookshelf.sniffer

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import dev.mcbookshelf.sniffer.commands.*
import dev.mcbookshelf.sniffer.config.DebuggerConfig
import dev.mcbookshelf.sniffer.dap.WebSocketServer
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.network.AuthPromptPayload
import dev.mcbookshelf.sniffer.network.AuthResponsePayload
import dev.mcbookshelf.sniffer.network.SetDapConnectedPayload
import dev.mcbookshelf.sniffer.network.SetDebugModePayload
import dev.mcbookshelf.sniffer.network.SetDebuggingPayload
import dev.mcbookshelf.sniffer.state.*
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.core.Registry
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import org.glassfish.tyrus.server.Server
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.logging.LogManager

/**
 * Main class of the Sniffer mod.
 * This mod provides debugging capabilities for Minecraft datapacks by adding breakpoints
 * and debugging features to help developers debug their datapack functions.
 *
 * @author Alumopper
 * @author theogiraudet
 */
class Sniffer : ModInitializer {

    companion object {
        private val logger = LoggerFactory.getLogger("sniffer")

        @JvmStatic
        val OBSERVED_PARTICLE: SimpleParticleType = FabricParticleTypes.simple()

        /**
         * WebSocket server instance for Debug Adapter Protocol communication.
         * This server allows IDE integration by implementing DAP over WebSockets.
         */
        @JvmStatic
        var webSocketServer: Server? = null
            private set
    }

    override fun onInitialize() {
        // Configure Java logging to reduce Tyrus logs
        try {
            val inputStream: InputStream? = Sniffer::class.java.getResourceAsStream("/logging.properties")
            if (inputStream != null) {
                LogManager.getLogManager().readConfiguration(inputStream)
                logger.info("Successfully configured Java logging from properties file")
            } else {
                logger.warn("Could not find logging.properties file")
            }
        } catch (e: Exception) {
            logger.error("Failed to configure Java logging", e)
        }

        // Load configuration
        DebuggerConfig.getInstance()
        logger.info("Sniffer configured to run on {}:{}/{}",
            DebuggerConfig.getInstance().host,
            DebuggerConfig.getInstance().port,
            DebuggerConfig.getInstance().path)

        // Reset and initialize debugger state
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            logger.info("Resetting debugger state")
            ServerReference.set(server)
            BreakpointManager.clear()
            DebugEventBus.clear()
            ScopeManager.get().clear()
            SteppingState.resetAll()
            DebugModeState.clear()
            ConnectionState.setConnected(false)
            logger.info("Debugger state reset complete")

            // Build the v2 action dispatcher (shared between DAP and chat entrypoints)
            SnifferDispatcher.init()
        }

        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register { _, _ -> FunctionPathRegistry.clear() }

        // Start WebSocket server for DAP communication using configured settings
        ServerLifecycleEvents.SERVER_STARTED.register { _ ->
            WebSocketServer.launch().ifPresent { wss -> webSocketServer = wss }
        }

        // Handle server shutdown to clean up resources
        ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
            logger.info("Shutting down debugger state")
            try {
                // Drop any paused execution before tearing the rest down.
                PausedExecutionStore.discard()
                DebugEventBus.fireShutdown()
                BreakpointManager.clear()
                DebugEventBus.clear()
                ScopeManager.get().clear()
                SteppingState.resetAll()
                PendingAuthRegistry.clearAll()
                logger.info("Debugger state shutdown complete")
            } catch (e: Exception) {
                logger.error("Error shutting down debugger state", e)
            }

            // Use the new clean WebSocket server shutdown method
            try {
                WebSocketServer.stopServer()
            } catch (e: Exception) {
                logger.error("Error stopping WebSocket server", e)
            }
        }

        @Suppress("DEPRECATION")
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(FunctionPathGetter())

        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath("sniffer", "observed_particle"), OBSERVED_PARTICLE)

        // Register custom argument types
        ArgumentTypeRegistry.registerArgumentType(
            Identifier.tryBuild("sniffer", "log")!!,
            LogArgumentType::class.java,
            SingletonArgumentInfo.contextFree(::LogArgumentType)
        )
        ArgumentTypeRegistry.registerArgumentType(
            Identifier.tryBuild("sniffer", "expr")!!,
            ExprArgumentType::class.java,
            SingletonArgumentInfo.contextFree(::ExprArgumentType)
        )

        PayloadTypeRegistry.clientboundPlay().register(SetDebugModePayload.TYPE, SetDebugModePayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(SetDapConnectedPayload.TYPE, SetDapConnectedPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(SetDebuggingPayload.TYPE, SetDebuggingPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(AuthPromptPayload.TYPE, AuthPromptPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(AuthResponsePayload.TYPE, AuthResponsePayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(AuthResponsePayload.TYPE) { payload, context ->
            val player = context.player()
            // Receiver runs on the server thread, safe to touch the registry directly.
            PendingAuthRegistry.resolve(player.uuid, payload.requestId, payload.accepted)
        }

        // Resync each player's HUD mirror on (re)connect so the server-side
        // DebugModeState persistence survives a client disconnect.
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            val enabled = DebugModeState.isEnabled(player.uuid)
            ServerPlayNetworking.send(player, SetDebugModePayload(enabled))
            ServerPlayNetworking.send(player, SetDapConnectedPayload(ConnectionState.isConnected()))
            ServerPlayNetworking.send(player, SetDebuggingPayload(SteppingState.isDebugging))
        }

        BreakPointCommand.onInitialize()
        LogCommand.onInitialize()
        AssertCommand.onInitialize()
        JvmtimerCommand.onInitialize()
        WatchCommand.onInitialize()
        DebugModeCommand.onInitialize()
    }
}
