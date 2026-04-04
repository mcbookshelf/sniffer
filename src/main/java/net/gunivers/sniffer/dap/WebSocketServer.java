package net.gunivers.sniffer.dap;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpointConfig;
import net.gunivers.sniffer.command.BreakPointCommand;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.glassfish.tyrus.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.gunivers.sniffer.config.DebuggerConfig;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashSet;

import static net.gunivers.sniffer.command.BreakPointCommand.continueExec;

/**
 * WebSocket server implementation for the Debug Adapter Protocol.
 * This class handles the communication between the debugging client (IDE) and the Minecraft server,
 * allowing remote debugging of datapacks through a WebSocket connection.
 *
 * @author theogiraudet
 */
public class WebSocketServer extends Endpoint {

    private static final Logger logger = LoggerFactory.getLogger("sniffer");

    private DapServer dapServer;
    private Launcher<IDebugProtocolClient> launcher;
    private final LinkedBlockingQueue<byte[]> messageQueue = new LinkedBlockingQueue<>();
    private Session currentSession;

    /**
     * Called when a WebSocket connection is established.
     * Sets up the Debug Adapter Protocol server and message handlers.
     *
     * @param session The WebSocket session
     * @param config The endpoint configuration
     */
    @Override
    public void onOpen(Session session, EndpointConfig config) {
        logger.info("WebSocket connected: {}", session.getRequestURI());
        
        // Store the session for later clean closure
        this.currentSession = session;
        
        // Configure a longer timeout for the session
        session.setMaxIdleTimeout(0); // No inactivity timeout
        
        // Configure buffer sizes
        session.setMaxTextMessageBufferSize(65536);
        session.setMaxBinaryMessageBufferSize(65536);
        
        // Add message handlers for text and binary
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                messageQueue.offer(message.getBytes());
            }
        });
        
        session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
            @Override
            public void onMessage(byte[] message) {
                messageQueue.offer(message);
            }
        });
        
        // Initialize a new DAP server for this session
        dapServer = new DapServer();
        InputStream in = new WebSocketInputStream(messageQueue);
        OutputStream out = new WebSocketOutputStream(session);
        launcher = DSPLauncher.createServerLauncher(dapServer, in, out);
        dapServer.setClient(launcher.getRemoteProxy());
        launcher.startListening();
    }

    /**
     * Called when a WebSocket connection is closed.
     * Cleans up resources and notifies the debugger state.
     *
     * @param session The WebSocket session
     * @param closeReason The reason for closing the connection
     */
    @Override
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("WebSocket closed: {}", closeReason);
        BreakPointCommand.clear();
        continueExec(DebuggerState.get().getCommandSource());
        cleanup();
    }

    /**
     * Called when an error occurs in the WebSocket connection.
     * Logs the error and cleans up resources.
     *
     * @param session The WebSocket session
     * @param throwable The error that occurred
     */
    @Override
    public void onError(Session session, Throwable throwable) {
        logger.error("Error in DAP server", throwable);
        cleanup();
    }
    
    /**
     * Cleans up resources when a connection is closed or an error occurs.
     * Stops the debug adapter and clears the message queue.
     */
    private void cleanup() {
        // Signal to the DAP server that it should terminate
        if (dapServer != null) {
            try {
                dapServer.exit();
            } catch (Exception e) {
                logger.error("Error shutting down DAP server", e);
            }
            dapServer = null;
        }
        
        // Clear the message queue
        messageQueue.clear();
        
        // Close the session if it is still open
        if (currentSession != null && currentSession.isOpen()) {
            try {
                currentSession.close();
            } catch (Exception e) {
                logger.error("Error closing WebSocket session", e);
            }
            currentSession = null;
        }
        
        // Stop the launcher listener if necessary
        if (launcher != null) {
            try {
                // No stopListening method, but we can help the GC
                launcher = null;
            } catch (Exception e) {
                logger.error("Error stopping launcher", e);
            }
        }
    }

    private static Server server;
    
    /**
     * Configuration class for the WebSocket server endpoint.
     * This class programmatically configures the WebSocket endpoint for the Debug Adapter Protocol,
     * allowing the server to handle connections on the configured path.
     */
    public static class WebSocketConfigurator implements ServerApplicationConfig {
        /**
         * Configures the WebSocket endpoint programmatically.
         * Creates a server endpoint configuration with the path from the debugger config.
         *
         * @param endpointClasses The set of endpoint classes to configure
         * @return A set containing the configured endpoint
         */
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
            Set<ServerEndpointConfig> configs = new HashSet<>();
            
            // Create a programmatic endpoint with the configured path
            String path = "/" + DebuggerConfig.getInstance().getPath();
            ServerEndpointConfig config = ServerEndpointConfig.Builder
                .create(WebSocketServer.class, path)
                .build();
                
            configs.add(config);
            return configs;
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            // We don't use annotated endpoints
            return new HashSet<>();
        }
    }


    private boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress("localhost", port));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Launches the WebSocket server using the configured port.
     * 
     * @return An Optional containing the server if successfully launched, or empty if failed
     */
    public static Optional<Server> launch() {
        // Use configured port from settings
        return launch(DebuggerConfig.getInstance().getPort());
    }

    /**
     * Launches the WebSocket server on the specified port.
     * 
     * @param port The port to run the server on
     * @return An Optional containing the server if successfully launched, or empty if failed
     */
    public static Optional<Server> launch(int port) {
        // Properly stop any existing server
        if (server != null) {
            try {
                server.stop();
                // Wait a moment for the system to release resources
                Thread.sleep(100);
            } catch (Exception e) {
                logger.error("Error stopping existing WebSocket server", e);
            } finally {
                server = null;
            }
        }
        
        // Get configuration values
        DebuggerConfig config = DebuggerConfig.getInstance();
        String path = config.getPath();

        final int maxAttempts = 10000;
        final int startPort = port;

        for (int i = 0; i < maxAttempts; i++) {
            port = startPort + i;
            Server server = new Server("localhost", port, "/", null, WebSocketConfigurator.class);
            try {
                server.start();
                logger.info("Jakarta WebSocket DAP server is running on ws://localhost:{}/{}", port, ""); // 如果需要 path，请替换
                return Optional.of(server);
            } catch (Exception e) {
                logger.debug("Failed to start server on port {}: {}", port, e.getMessage());
                try {
                    server.stop();
                } catch (Exception stopEx) {
                    logger.debug("Error stopping failed server on port {}: {}", port, stopEx.getMessage());
                }
                // 继续尝试下一个端口
            }
        }
        logger.error("No available port found in range {} - {}", startPort, startPort + maxAttempts - 1);
        return Optional.empty();
    }
    
    /**
     * Stops the WebSocket server gracefully.
     * This method ensures all connections are closed properly before shutting down.
     */
    public static void stopServer() {
        if (server != null) {
            try {
                server.stop();
                logger.info("WebSocket server stopped");
            } catch (Exception e) {
                logger.error("Error stopping WebSocket server", e);
            } finally {
                server = null;
            }
        }
    }
}
