package dev.mcbookshelf.sniffer.features.trace

/**
 * The trace currently open, if any, and how a caller tells whether it is the one that opened it.
 *
 * A single trace runs at a time, so its id is what says whether one is open at all.
 * Read from the WebSocket thread and written from the server thread.
 *
 * @author theogiraudet
 */
object TraceState {

    /** Id of the open trace, `null` when none is. */
    @Volatile
    var currentTraceId: Int? = null

    /** The last id handed out, which only ever grows. */
    @Volatile
    private var lastTraceId: Int = 0

    /** Hands out the next id and opens a trace under it. */
    fun open(): Int {
        val traceId = lastTraceId + 1
        lastTraceId = traceId
        currentTraceId = traceId
        return traceId
    }

    /** Closes the open trace, if any, and reports which one it was. */
    fun close(): Int? {
        val traceId = currentTraceId
        currentTraceId = null
        return traceId
    }
}
