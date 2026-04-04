package net.gunivers.sniffer.dap;

import jakarta.websocket.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Adapts an OutputStream to send data through WebSocket.
 * This class provides a bridge between the Java stream-based API and
 * the WebSocket message-based API by buffering written data
 * and then sending it as a WebSocket message when flushed.
 * <p>
 * Data is accumulated in an internal buffer until flush() is called,
 * at which point it is converted to a string and sent as a WebSocket text message.
 *
 * @author theogiraudet
 */
public class WebSocketOutputStream extends OutputStream {
    /** The WebSocket session to send messages to */
    private final Session session;
    /** Buffer to accumulate data until flushed */
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /**
     * Creates a new WebSocketOutputStream.
     * 
     * @param session The WebSocket session to send data to
     */
    public WebSocketOutputStream(Session session) {
        this.session = session;
    }

    /**
     * Writes a single byte to the internal buffer.
     * This does not send any data over the WebSocket until flush() is called.
     * 
     * @param b The byte to write
     */
    @Override
    public void write(int b) {
        buffer.write(b);
    }

    /**
     * Flushes the buffered data by sending it as a WebSocket text message.
     * The internal buffer is reset after sending.
     *
     */
    @Override
    public void flush() {
        String message = buffer.toString(StandardCharsets.UTF_8);
        buffer.reset();
        if (session.isOpen()) {
            session.getAsyncRemote().sendText(message);
        }
    }

    /**
     * Closes this output stream by flushing any remaining data.
     * 
     * @throws IOException If an error occurs during flushing
     */
    @Override
    public void close() throws IOException {
        flush();
    }
}