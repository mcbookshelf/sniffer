package dev.mcbookshelf.sniffer.dap

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * Bridges the messages of a WebSocket and the stream API the DAP launcher reads from.
 * Messages are taken from [queue] one after the other, and a read blocks while that queue is empty.
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
