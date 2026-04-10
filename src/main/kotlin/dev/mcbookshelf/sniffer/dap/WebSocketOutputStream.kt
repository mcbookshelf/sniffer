package dev.mcbookshelf.sniffer.dap

import jakarta.websocket.Session
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Adapts an OutputStream to send data through WebSocket.
 * This class provides a bridge between the Java stream-based API and
 * the WebSocket message-based API by buffering written data
 * and then sending it as a WebSocket message when flushed.
 *
 * Data is accumulated in an internal buffer until flush() is called,
 * at which point it is converted to a string and sent as a WebSocket text message.
 *
 * @author theogiraudet
 */
class WebSocketOutputStream(private val session: Session) : OutputStream() {

    private val buffer = ByteArrayOutputStream()

    override fun write(b: Int) {
        buffer.write(b)
    }

    override fun flush() {
        val message = buffer.toString(StandardCharsets.UTF_8)
        buffer.reset()
        if (session.isOpen) {
            session.basicRemote.sendText(message)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        flush()
    }
}
