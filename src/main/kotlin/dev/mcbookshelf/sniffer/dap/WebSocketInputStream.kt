package dev.mcbookshelf.sniffer.dap

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * Adapts WebSocket messages into an InputStream.
 * This class provides a bridge between the WebSocket message-based API and
 * the Java stream-based API by converting incoming WebSocket messages
 * into a continuous input stream that can be consumed by stream-based APIs.
 *
 * The class blocks when no data is available and waits for messages
 * to arrive on the provided message queue.
 *
 * @author theogiraudet
 */
class WebSocketInputStream(private val queue: LinkedBlockingQueue<ByteArray>) : InputStream() {

    private var currentStream: ByteArrayInputStream? = null

    @Throws(IOException::class)
    override fun read(): Int {
        if (currentStream == null || currentStream!!.available() == 0) {
            try {
                val nextMessage = queue.take()
                currentStream = ByteArrayInputStream(nextMessage)
            } catch (e: InterruptedException) {
                throw IOException("Interrupted while waiting for a WebSocket message", e)
            }
        }
        return currentStream!!.read()
    }
}
