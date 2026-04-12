package dev.mcbookshelf.sniffer.dap

import jakarta.websocket.*
import jakarta.websocket.server.ServerApplicationConfig
import jakarta.websocket.server.ServerEndpointConfig
import dev.mcbookshelf.sniffer.config.DebuggerConfig
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.input.ContinueInput
import dev.mcbookshelf.sniffer.network.AuthPromptPayload
import dev.mcbookshelf.sniffer.state.ConnectionState
import dev.mcbookshelf.sniffer.state.PendingAuthRegistry
import dev.mcbookshelf.sniffer.state.ServerReference
import dev.mcbookshelf.sniffer.state.SteppingState
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.players.NameAndId
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.glassfish.tyrus.server.Server
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * WebSocket server implementation for the Debug Adapter Protocol.
 * This class handles the communication between the debugging client (IDE) and the Minecraft server,
 * allowing remote debugging of datapacks through a WebSocket connection.
 *
 * @author theogiraudet
 */
class WebSocketServer : Endpoint() {

    companion object {
        private val logger = LoggerFactory.getLogger("sniffer")
        private var server: Server? = null

        /**
         * Launches the WebSocket server using the configured port.
         *
         * @return An Optional containing the server if successfully launched, or empty if failed
         */
        @JvmStatic
        fun launch(): Optional<Server> =
            launch(DebuggerConfig.getInstance().host, DebuggerConfig.getInstance().port)

        /**
         * Launches the WebSocket server on the specified host and port.
         *
         * @param host The host interface to bind to (e.g. "localhost" or "0.0.0.0")
         * @param port The port to run the server on
         * @return An Optional containing the server if successfully launched, or empty if failed
         */
        @JvmStatic
        fun launch(host: String, port: Int): Optional<Server> {
            // Properly stop any existing server
            server?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                    logger.error("Error stopping existing WebSocket server", e)
                } finally {
                    server = null
                }
            }

            val maxAttempts = 10

            for (i in 0 until maxAttempts) {
                val currentPort = port + i
                val s = Server(host, currentPort, "/", null, WebSocketConfigurator::class.java)
                try {
                    s.start()
                    logger.info("Jakarta WebSocket DAP server is running on ws://{}:{}/{}", host, currentPort, "")
                    return Optional.of(s)
                } catch (e: Exception) {
                    logger.debug("Failed to start server on port {}: {}", currentPort, e.message)
                    try {
                        s.stop()
                    } catch (stopEx: Exception) {
                        logger.debug("Error stopping failed server on port {}: {}", currentPort, stopEx.message)
                    }
                }
            }
            logger.error("No available port found in range {} - {}", port, port + maxAttempts - 1)
            return Optional.empty()
        }

        /**
         * Stops the WebSocket server gracefully.
         * This method ensures all connections are closed properly before shutting down.
         */
        @JvmStatic
        fun stopServer() {
            server?.let {
                try {
                    it.stop()
                    logger.info("WebSocket server stopped")
                } catch (e: Exception) {
                    logger.error("Error stopping WebSocket server", e)
                } finally {
                    server = null
                }
            }
        }
    }

    private var dapServer: DapServer? = null
    private var launcher: Launcher<IDebugProtocolClient>? = null
    private val messageQueue = LinkedBlockingQueue<ByteArray>()
    private var currentSession: Session? = null

    override fun onOpen(session: Session, config: EndpointConfig) {
        logger.info("WebSocket connected: {}", session.requestURI)

        currentSession = session

        session.maxIdleTimeout = 0
        session.maxTextMessageBufferSize = 65536
        session.maxBinaryMessageBufferSize = 65536

        session.addMessageHandler(object : MessageHandler.Whole<String> {
            override fun onMessage(message: String) {
                messageQueue.offer(message.toByteArray())
            }
        })

        session.addMessageHandler(object : MessageHandler.Whole<ByteArray> {
            override fun onMessage(message: ByteArray) {
                messageQueue.offer(message)
            }
        })

        val cfg = DebuggerConfig.getInstance()
        if (!cfg.authEnabled) {
            startDap(session)
            return
        }

        val username = session.requestParameterMap["user"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        val server = ServerReference.get()

        // Singleplayer: the user param is optional — default to the host player.
        // Multiplayer: the user param is mandatory so we know which player to prompt.
        if (server.isSingleplayer && username == null) {
            val host = server.singleplayerProfile
            if (host == null) {
                reject(session, "cannot determine singleplayer host")
                return
            }
            server.execute {
                val player = server.playerList.getPlayer(host.id) ?: run {
                    reject(session, "host player not online")
                    return@execute
                }
                promptPlayer(session, player, cfg)
            }
            return
        }

        if (username == null) {
            reject(session, "missing 'user' parameter (required in multiplayer)")
            return
        }

        server.execute {
            val player = server.playerList.getPlayerByName(username)
            if (player == null) {
                reject(session, "player '$username' not online")
                return@execute
            }
            if (!server.playerList.isOp(NameAndId(player.gameProfile))) {
                reject(session, "player '$username' is not an operator")
                return@execute
            }
            promptPlayer(session, player, cfg)
        }
    }

    private fun promptPlayer(session: Session, player: net.minecraft.server.level.ServerPlayer, cfg: DebuggerConfig) {
        val requestId = UUID.randomUUID()
        val description = session.requestURI.toString()
        PendingAuthRegistry.register(
            PendingAuthRegistry.PendingAuth(
                requestId = requestId,
                session = session,
                playerUuid = player.uuid,
                onApproved = { startDap(session) },
                onRejected = { /* session close handled by registry */ },
            ),
            cfg.authPromptTimeoutSeconds,
        )
        ServerPlayNetworking.send(player, AuthPromptPayload(requestId, description, cfg.authPromptTimeoutSeconds))
    }

    private fun startDap(session: Session) {
        ConnectionState.setConnected(true)

        dapServer = DapServer()
        val `in` = WebSocketInputStream(messageQueue)
        val out = WebSocketOutputStream(session)
        launcher = DSPLauncher.createServerLauncher(dapServer, `in`, out)
        dapServer!!.setClient(launcher!!.remoteProxy)
        launcher!!.startListening()
    }

    private fun reject(session: Session, reason: String) {
        logger.warn("Rejecting DAP connection: {}", reason)
        try {
            if (session.isOpen) {
                session.close(CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason))
            }
        } catch (e: Exception) {
            logger.warn("Failed to close rejected session: {}", e.message)
        }
    }

    override fun onClose(session: Session, closeReason: CloseReason) {
        logger.info("WebSocket closed: {}", closeReason)
        PendingAuthRegistry.cancel(session)
        val server = ServerReference.get()
        SnifferDispatcher.get().dispatch(ContinueInput, Context(server.createCommandSourceStack(), server))
        SteppingState.resetAll()
        cleanup()
    }

    override fun onError(session: Session, throwable: Throwable) {
        logger.error("Error in DAP server", throwable)
        PendingAuthRegistry.cancel(session)
        cleanup()
    }

    private fun cleanup() {
        ConnectionState.setConnected(false)

        dapServer?.let {
            try {
                it.exit()
            } catch (e: Exception) {
                logger.error("Error shutting down DAP server", e)
            }
            dapServer = null
        }

        messageQueue.clear()

        currentSession?.let {
            if (it.isOpen) {
                try {
                    it.close()
                } catch (e: Exception) {
                    logger.error("Error closing WebSocket session", e)
                }
            }
            currentSession = null
        }

        launcher = null
    }

    /**
     * Configuration class for the WebSocket server endpoint.
     */
    class WebSocketConfigurator : ServerApplicationConfig {
        override fun getEndpointConfigs(endpointClasses: Set<Class<out Endpoint>>): Set<ServerEndpointConfig> {
            val path = "/${DebuggerConfig.getInstance().path}"
            val config = ServerEndpointConfig.Builder
                .create(WebSocketServer::class.java, path)
                .build()
            return setOf(config)
        }

        override fun getAnnotatedEndpointClasses(scanned: Set<Class<*>>): Set<Class<*>> = emptySet()
    }
}
