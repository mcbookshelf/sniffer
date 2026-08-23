package dev.mcbookshelf.sniffer.dap

import jakarta.websocket.Session
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Bridges the stream API the DAP launcher writes to and the message API of a WebSocket.
 * Written bytes pile up in a buffer, and a flush turns them into a single text message.
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
