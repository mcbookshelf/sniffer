package dev.mcbookshelf.sniffer.dap

import dev.mcbookshelf.sniffer.config.DebuggerConfig
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.features.callstack.ClearScopesInput
import dev.mcbookshelf.sniffer.features.stepping.ContinueInput
import dev.mcbookshelf.sniffer.features.stepping.ResetSteppingInput
import dev.mcbookshelf.sniffer.features.stepping.SteppingState
import dev.mcbookshelf.sniffer.network.AuthPromptPayload
import jakarta.websocket.*
import jakarta.websocket.server.ServerApplicationConfig
import jakarta.websocket.server.ServerEndpointConfig
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.players.NameAndId
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.glassfish.tyrus.server.Server
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * WebSocket endpoint carrying the Debug Adapter Protocol between the IDE and the Minecraft server.
 * A single session is accepted at a time, and it only starts a [DapServer] once the player has approved it.
 *
 * @author theogiraudet
 * @author Alumopper
 */
class WebSocketServer : Endpoint() {

    companion object {
        private val logger = LoggerFactory.getLogger("sniffer")
        private var server: Server? = null

        /** Cap on messages buffered per session, which matters before authentication, where nothing drains the queue. */
        private const val MAX_QUEUED_MESSAGES = 1024

        /**
         * Launches the WebSocket server on the configured host and port.
         *
         * @return the running server, or empty if it could not be started
         */
        @JvmStatic
        fun launch(): Optional<Server> =
            launch(DebuggerConfig.getInstance().host, DebuggerConfig.getInstance().port)

        /**
         * Launches the WebSocket server, replacing any server already running.
         *
         * @param host the interface to bind to, such as `localhost` or `0.0.0.0`
         * @param port the port to listen on
         * @return the running server, or empty if it could not be started
         */
        @JvmStatic
        fun launch(host: String, port: Int): Optional<Server> {
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
                    server = s
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
         * Stops the WebSocket server, closing every open connection first.
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
    private var launcher: Launcher<DapRemote>? = null

    // Bounded so an unauthenticated client cannot grow the heap while the approval prompt is pending.
    private val messageQueue = LinkedBlockingQueue<ByteArray>(MAX_QUEUED_MESSAGES)
    private var currentSession: Session? = null

    override fun onOpen(session: Session, config: EndpointConfig) {
        logger.info("WebSocket connected: {}", session.requestURI)

        currentSession = session

        session.maxIdleTimeout = 0
        session.maxTextMessageBufferSize = 65536
        session.maxBinaryMessageBufferSize = 65536

        // Only one DAP session is supported: a second client would overwrite the listeners of the first one.
        if (ConnectionState.isConnected()) {
            reject(session, "another debugger is already attached")
            return
        }

        session.addMessageHandler(object : MessageHandler.Whole<String> {
            override fun onMessage(message: String) {
                enqueue(session, message.toByteArray())
            }
        })

        session.addMessageHandler(object : MessageHandler.Whole<ByteArray> {
            override fun onMessage(message: ByteArray) {
                enqueue(session, message)
            }
        })

        // Recorded whether or not the connection has to be approved: it is also who the debug console runs as.
        val username = session.requestParameterMap["user"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        ConnectionState.setAttachedUser(username)

        val cfg = DebuggerConfig.getInstance()
        if (!cfg.authEnabled) {
            startDap(session)
            return
        }

        val server = runCatching { ServerReference.get() }.getOrNull()
        if (server == null) {
            reject(session, "Minecraft server not available")
            return
        }

        // In singleplayer the user parameter is optional and defaults to the host player, who needs no op entry.
        // In multiplayer it is mandatory, since it names the player to prompt.
        if (server.isSingleplayer) {
            val host = server.singleplayerProfile
            if (host == null) {
                reject(session, "cannot determine singleplayer host")
                return
            }
            if (username == null || username.equals(host.name, ignoreCase = true)) {
                server.execute {
                    val player = server.playerList.getPlayer(host.id) ?: run {
                        reject(session, "host player not online")
                        return@execute
                    }
                    promptPlayer(session, player, cfg)
                }
                return
            }
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

    private fun enqueue(session: Session, message: ByteArray) {
        if (!messageQueue.offer(message)) {
            reject(session, "message backlog exceeded before session was approved")
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
        launcher = DebugLauncher.Builder<DapRemote>()
            .setLocalServices(DapEndpointsRegistry.buildLocalServices(dapServer!!))
            .setRemoteInterfaces(DapEndpointsRegistry.buildRemoteInterfaces())
            // Required: with more than one remote interface LSP4J proxies through this loader, and defaults it to null.
            .setClassLoader(DapEndpointsRegistry::class.java.classLoader)
            .setInput(`in`)
            .setOutput(out)
            .create()
        DapClient.attach(launcher!!.remoteProxy)
        DapClient.of(StandardDapClient::class.java)?.let { dapServer!!.setClient(it) }
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
        // The server may already be gone if the close is part of shutdown.
        runCatching { ServerReference.get() }.getOrNull()?.let { server ->
            if (SteppingState.isDebugging) {
                SnifferDispatcher.get().dispatch(ContinueInput, Context(server.createCommandSourceStack()))
            }
        }
        cleanup()
    }

    override fun onError(session: Session, throwable: Throwable) {
        logger.error("Error in DAP server", throwable)
        PendingAuthRegistry.cancel(session)
        cleanup()
    }

    private fun cleanup() {
        ConnectionState.setConnected(false)
        ConnectionState.setAttachedUser(null)
        // Detach first, so anything the session ends below writes to nobody rather than to a dead socket.
        DapClient.detach()
        endDebugSession()

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
     * Ends the debug session the editor was driving, through the dispatcher rather than by reaching into
     * the state of each feature.
     * Clearing the scopes is what tells the observers of the control flow that what they were following is over,
     * which nothing else on this path does.
     */
    private fun endDebugSession() {
        val source = runCatching { ServerReference.getCommandSource() }.getOrNull() ?: return
        val context = Context(source)
        runCatching {
            SnifferDispatcher.get().dispatch(ResetSteppingInput, context)
            SnifferDispatcher.get().dispatch(ClearScopesInput, context)
        }.onFailure { logger.warn("Could not end the debug session cleanly", it) }
    }

    /**
     * Declares the endpoint and the path it is served on.
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
