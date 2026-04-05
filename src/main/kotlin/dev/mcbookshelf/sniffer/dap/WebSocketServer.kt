package dev.mcbookshelf.sniffer.dap

import jakarta.websocket.*
import jakarta.websocket.server.ServerApplicationConfig
import jakarta.websocket.server.ServerEndpointConfig
import dev.mcbookshelf.sniffer.config.DebuggerConfig
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.input.ContinueInput
import dev.mcbookshelf.sniffer.state.ConnectionState
import dev.mcbookshelf.sniffer.state.ServerReference
import dev.mcbookshelf.sniffer.state.SteppingState
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
            launch(DebuggerConfig.getInstance().port)

        /**
         * Launches the WebSocket server on the specified port.
         *
         * @param port The port to run the server on
         * @return An Optional containing the server if successfully launched, or empty if failed
         */
        @JvmStatic
        fun launch(port: Int): Optional<Server> {
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
                val s = Server("localhost", currentPort, "/", null, WebSocketConfigurator::class.java)
                try {
                    s.start()
                    logger.info("Jakarta WebSocket DAP server is running on ws://localhost:{}/{}", currentPort, "")
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

        ConnectionState.clientConnected = true

        dapServer = DapServer()
        val `in` = WebSocketInputStream(messageQueue)
        val out = WebSocketOutputStream(session)
        launcher = DSPLauncher.createServerLauncher(dapServer, `in`, out)
        dapServer!!.setClient(launcher!!.remoteProxy)
        launcher!!.startListening()
    }

    override fun onClose(session: Session, closeReason: CloseReason) {
        logger.info("WebSocket closed: {}", closeReason)
        val server = ServerReference.get()
        SnifferDispatcher.get().dispatch(ContinueInput, Context(server.createCommandSourceStack(), server))
        SteppingState.resetAll()
        cleanup()
    }

    override fun onError(session: Session, throwable: Throwable) {
        logger.error("Error in DAP server", throwable)
        cleanup()
    }

    private fun cleanup() {
        ConnectionState.clientConnected = false

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
