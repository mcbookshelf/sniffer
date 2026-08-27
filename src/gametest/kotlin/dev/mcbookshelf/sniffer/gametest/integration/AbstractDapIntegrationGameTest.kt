package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.config.DebuggerConfig
import dev.mcbookshelf.sniffer.dap.WebSocketOutputStream
import dev.mcbookshelf.sniffer.dap.WebSocketServer
import dev.mcbookshelf.sniffer.state.ConnectionState
import jakarta.websocket.ClientEndpointConfig
import jakarta.websocket.CloseReason
import jakarta.websocket.Endpoint
import jakarta.websocket.EndpointConfig
import jakarta.websocket.MessageHandler
import jakarta.websocket.Session
import net.minecraft.gametest.framework.GameTestSequence
import org.eclipse.lsp4j.debug.ContinuedEventArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.glassfish.tyrus.client.ClientManager
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.URI
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A DAP client, connected to the mod's own WebSocket server in the running game.
 *
 * It speaks LSP4J's DAP over a genuine socket, so requests travel the transport the editor uses and the events the adapter pushes back arrive on a listener thread rather than being returned inline.
 * Those events are queued as they arrive, since an event fired before anything is waiting for it would otherwise be lost.
 *
 * A single instance of this class is reused for every method that runs, so [initDapClient] tears down whatever the last one left connected rather than assuming a fresh object.
 */
abstract class AbstractDapIntegrationGameTest {

    private var incoming: ClientMessageStream? = null
    private var session: Session? = null

    /** Events the adapter pushed to this client, in arrival order. */
    val events = RecordingDapClient()

    /** The port the mod's WebSocket server is currently listening on, for opening a further connection alongside this one. */
    var dapPort: Int = 0
        private set

    /** The reasons the server gave for closing a connection, in arrival order. */
    val closures: Queue<CloseReason> = ConcurrentLinkedQueue()

    /**
     * Connects a DAP client to the mod's WebSocket server and returns the remote proxy through which requests are sent.
     *
     * The server is relaunched on a free port, so the connection never depends on which port the configured one fell back to.
     *
     * @param authEnabled whether the connection has to be approved in game before it becomes a DAP session.
     *   Off by default, since a server with nobody online has no one to answer the prompt and the connection would hang waiting for it.
     * @param user the player the connection asks to be approved by, sent as the `user` query parameter an editor puts in its URL.
     * @param promptTimeoutSeconds how long the prompt stands before the connection is dropped.
     */
    fun initDapClient(
        authEnabled: Boolean = false,
        user: String? = null,
        promptTimeoutSeconds: Int = DEFAULT_PROMPT_TIMEOUT_SECONDS,
    ): IDebugProtocolServer {
        // A previous client that was never closed would go on draining the message queue from under this one.
        closeDapClient()

        val config = DebuggerConfig.getInstance()
        config.authEnabled = authEnabled
        config.authPromptTimeoutSeconds = promptTimeoutSeconds

        // The flag survives a session that was never closed cleanly, and would otherwise refuse the connection opened below.
        ConnectionState.setConnected(false)

        val queue = LinkedBlockingQueue<ByteArray>()
        val input = ClientMessageStream(queue)
        incoming = input

        dapPort = ServerSocket(0).use { it.localPort }
        WebSocketServer.launch(config.host, dapPort)

        val endpoint = object : Endpoint() {
            override fun onOpen(session: Session, config: EndpointConfig) {
                session.maxIdleTimeout = 0
                session.addMessageHandler(object : MessageHandler.Whole<String> {
                    override fun onMessage(message: String) {
                        queue.put(message.toByteArray())
                    }
                })
            }

            // A refused connection is not a failed handshake: the socket opens and the mod closes it once it knows who is asking.
            override fun onClose(session: Session, reason: CloseReason) {
                closures.add(reason)
            }
        }

        val session = ClientManager.createClient().connectToServer(
            endpoint,
            ClientEndpointConfig.Builder.create().build(),
            URI("ws://${config.host}:$dapPort/${config.path}" + if (user == null) "" else "?user=$user"),
        )
        this.session = session

        val launcher = DSPLauncher.createClientLauncher(
            events,
            input,
            WebSocketOutputStream(session),
        )
        launcher.startListening()
        return launcher.remoteProxy
    }

    /**
     * Closes the connection and ends the test, which is how every test here finishes.
     * Ending any other way leaves the client connected, and the mod would refuse the next one.
     */
    fun GameTestSequence.thenSucceedAndClose() {
        thenExecute { closeDapClient() }.thenSucceed()
    }

    /**
     * Closes the DAP connection.
     * The mod accepts a single session at a time, so a client left connected has the next connection refused.
     */
    fun closeDapClient() {
        session?.let { runCatching { it.close() } }
        session = null
        incoming?.close()
        incoming = null
        events.clear()
        closures.clear()
    }

    /**
     * Client side of the transport: the same queue to stream bridge the mod uses, but with an end of stream on [close].
     *
     * [dev.mcbookshelf.sniffer.dap.WebSocketInputStream] blocks forever on an empty queue, so the only way to stop LSP4J's listener thread is to interrupt it, which it reports as an error on every teardown.
     * Ending the stream instead lets that thread finish quietly.
     */
    private class ClientMessageStream(private val queue: BlockingQueue<ByteArray>) : InputStream() {

        @Volatile
        private var closed = false
        private var current: ByteArrayInputStream? = null

        override fun read(): Int {
            while (true) {
                current?.let { if (it.available() > 0) return it.read() }
                if (closed) return -1
                val next = queue.poll(POLL_MS, TimeUnit.MILLISECONDS) ?: continue
                current = ByteArrayInputStream(next)
            }
        }

        override fun close() {
            closed = true
        }

        private companion object {
            /** How long a closed stream can take to report it ended. */
            const val POLL_MS = 50L
        }
    }

    private companion object {
        /** Long enough that a prompt does not lapse before it can be answered, short enough that waiting one out stays quick. */
        const val DEFAULT_PROMPT_TIMEOUT_SECONDS = 60
    }

    /** Holds on to the events the adapter pushes, which arrive whenever it decides rather than when they are wanted. */
    class RecordingDapClient : IDebugProtocolClient {
        val initialized: Queue<Unit> = ConcurrentLinkedQueue()
        val stopped: Queue<StoppedEventArguments> = ConcurrentLinkedQueue()
        val continued: Queue<ContinuedEventArguments> = ConcurrentLinkedQueue()
        val terminated: Queue<TerminatedEventArguments> = ConcurrentLinkedQueue()
        val output: Queue<OutputEventArguments> = ConcurrentLinkedQueue()

        override fun initialized() {
            initialized.add(Unit)
        }

        override fun stopped(args: StoppedEventArguments) {
            stopped.add(args)
        }

        override fun continued(args: ContinuedEventArguments) {
            continued.add(args)
        }

        override fun terminated(args: TerminatedEventArguments) {
            terminated.add(args)
        }

        override fun output(args: OutputEventArguments) {
            output.add(args)
        }

        fun clear() {
            initialized.clear()
            stopped.clear()
            continued.clear()
            terminated.clear()
            output.clear()
        }
    }
}