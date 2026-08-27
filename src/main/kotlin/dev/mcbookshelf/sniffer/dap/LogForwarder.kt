package dev.mcbookshelf.sniffer.dap

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Mirrors the game log into the debug console of the attached client.
 *
 * An appender on the root logger picks up every line the game writes, Sniffer's own and every other mod's,
 * and a thread of its own is what sends them.
 * The game must never wait on the IDE: writing a WebSocket frame from the thread that logged would hold the server thread
 * for as long as the reader on the other end takes, so lines are queued instead, and dropped once the queue is full.
 *
 * @author theogiraudet
 */
object LogForwarder {

    private val LOGGER = LoggerFactory.getLogger("sniffer")

    private const val APPENDER_NAME = "sniffer-dap"
    private const val THREAD_NAME = "sniffer-log-forwarder"
    private const val PATTERN = "[%d{HH:mm:ss}] [%t/%level] (%logger{1}) %msg%n%throwable"

    /** How many lines may be waiting to be sent before further ones are dropped. */
    private const val QUEUE_CAPACITY = 4096

    /** How long the sender waits on an empty queue before checking whether it should stop. */
    private const val POLL_MS = 100L

    private val queue = ArrayBlockingQueue<String>(QUEUE_CAPACITY)

    private var appender: Appender? = null

    /** The thread doing the sending, which is also what tells [DapAppender] which log lines are the forwarding's own. */
    @Volatile
    private var sender: Thread? = null

    /**
     * Starts mirroring the game log, each line handed to [sink] on a thread of the forwarder's own.
     * Calling it again while already running does nothing.
     */
    @Synchronized
    fun start(sink: (String) -> Unit) {
        if (appender != null) return

        val context = runCatching { LogManager.getContext(false) as LoggerContext }.getOrElse {
            LOGGER.warn("Cannot mirror the game log: log4j is not the logging backend", it)
            return
        }

        queue.clear()
        // The field is set before the thread runs, since the thread reads it as its own stop condition and the appender reads it as its guard.
        val thread = Thread({ sendLoop(sink) }, THREAD_NAME).apply { isDaemon = true }
        sender = thread
        thread.start()

        val created = DapAppender().apply { start() }
        appender = created
        context.configuration.addAppender(created)
        context.configuration.rootLogger.addAppender(created, null, null)
        context.updateLoggers()
    }

    /**
     * Stops mirroring the game log. Calling it while not running does nothing.
     */
    @Synchronized
    fun stop() {
        val current = appender ?: return
        appender = null

        runCatching {
            val context = LogManager.getContext(false) as LoggerContext
            context.configuration.rootLogger.removeAppender(APPENDER_NAME)
            context.updateLoggers()
        }
        current.stop()

        // Clearing the field is what stops the sender. The interrupt only saves it the wait for its next poll to lapse.
        val thread = sender
        sender = null
        thread?.interrupt()
        queue.clear()
    }

    /**
     * Sends what the appender queued, until [stop] clears [sender] and this thread reads that it is no longer the one sending.
     * The interrupt flag cannot be that signal: a send interrupted mid write reports it as an exception, which clears the flag,
     * and the swallowed exception would leave the thread looping forever.
     */
    private fun sendLoop(sink: (String) -> Unit) {
        try {
            while (Thread.currentThread() === sender) {
                val line = queue.poll(POLL_MS, TimeUnit.MILLISECONDS) ?: continue
                // A failed send must not kill the sender, and it must not be logged either: that line would come straight back here.
                runCatching { sink(line) }
            }
        } catch (_: InterruptedException) {
            // Stopping.
        }
    }

    /**
     * Formats each event on the thread that logged it, since log4j reuses the event objects, and queues the result.
     */
    private class DapAppender : AbstractAppender(
        APPENDER_NAME,
        null,
        PatternLayout.newBuilder().setPattern(PATTERN).build(),
        true,
        Property.EMPTY_ARRAY,
    ) {
        override fun append(event: LogEvent) {
            // Anything logged in the sender thread is ignored, otherwise a send that logs feeds itself forever.
            if (Thread.currentThread() === sender) return
            // Only what a server would write to its log file is mirrored.
            // Minecraft keeps its root logger at INFO, so this drops nothing in production and, in a development environment,
            // drops the account the mod keeps of every request it answers, which describes the session rather than the pack.
            if (!event.level.isMoreSpecificThan(Level.INFO)) return
            queue.offer(layout.toSerializable(event) as String)
        }
    }
}
