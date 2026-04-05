package dev.mcbookshelf.sniffer.state

import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Paths
import java.util.Optional
import java.util.regex.Pattern

/**
 * Owns all breakpoint storage, path resolution, and breakpoint-hit queries.
 *
 * This is the single source of truth for "should execution pause at this
 * location?" — combining breakpoint bookkeeping with the duplicate-hit
 * guard that prevents re-triggering at the same position.
 */
object BreakpointManager {

    private val LOGGER = LoggerFactory.getLogger("sniffer")
    private val PATH_PATTERN: Pattern =
        Pattern.compile("data/(?<namespace>.+)/function/(?<path>.+)\\.mcfunction")

    private data class Breakpoint(val id: Int, val line: Int)

    private class FunctionBreakpoints(
        val functionMcPath: String,
        val functionPath: String,
    ) {
        val breakpoints: MutableMap<Int, Breakpoint> = HashMap()
    }

    private val breakpoints: MutableMap<String, FunctionBreakpoints> = HashMap()
    private var nextId: Int = 0

    private val scopeManager: ScopeManager get() = ScopeManager.get()

    /**
     * Normalizes a filesystem path to a canonical string for consistent map lookups.
     *
     * Uses [java.nio.file.Path.toRealPath] to canonicalize drive-letter case on
     * Windows (VSCode sends `e:\...` while Minecraft's Path API yields `E:\...`).
     * Falls back to plain absolute-normalize if the file cannot be resolved.
     */
    private fun normalizePath(filePath: String): String {
        val p = Paths.get(filePath)
        return try {
            p.toRealPath().toString()
        } catch (_: IOException) {
            p.toAbsolutePath().normalize().toString()
        }
    }

    // ── Breakpoint-hit query ────────────────────────────────────────

    /**
     * Whether execution should stop at [mcpath]:[line].
     *
     * A stop is triggered when a breakpoint exists at this position AND
     * the debugger isn't already paused at this exact position (to avoid
     * re-triggering on the same line after a step).
     */
    @JvmStatic
    fun mustStop(mcpath: String?, line: Int): Boolean =
        contains(mcpath, line) && !isAtCurrentPosition(mcpath, line)

    private fun isAtCurrentPosition(file: String?, line: Int): Boolean {
        val functionName = scopeManager.currentScope.map { it.function }.orElse("")
        val functionLine = scopeManager.currentScope.map { it.line }.orElse(-1)
        return file == functionName && line == functionLine
    }

    // ── Registration ────────────────────────────────────────────────

    /**
     * Registers a breakpoint at [line] in the file at [filePath].
     *
     * @return the new breakpoint's unique ID, or empty if the file could
     *   not be resolved to a Minecraft function path.
     */
    @JvmStatic
    fun addBreakpoint(filePath: String?, line: Int): Optional<Int> {
        if (filePath == null) {
            LOGGER.warn("Attempted to add breakpoint with null file path")
            return Optional.empty()
        }
        val normalized = normalizePath(filePath)

        val mcpath = fileToMcPath(normalized)
        if (mcpath != null) {
            val funBps = breakpoints.getOrPut(normalized) { FunctionBreakpoints(mcpath, normalized) }
            val id = nextId++
            funBps.breakpoints[line] = Breakpoint(id, line)
            return Optional.of(id)
        }

        LOGGER.warn("Failed to add breakpoint at {}:{} - Could not convert to MC path", filePath, line)
        return Optional.empty()
    }

    /** Removes all breakpoints for the file at [filePath]. */
    @JvmStatic
    fun clearBreakpoints(filePath: String?) {
        if (filePath == null) {
            LOGGER.warn("Attempted to clear breakpoints with null file path")
            return
        }
        breakpoints.remove(normalizePath(filePath))
        LOGGER.debug("Cleared all breakpoints for {}", filePath)
    }

    /** Whether a breakpoint is set at [mcpath]:[line]. */
    @JvmStatic
    fun contains(mcpath: String?, line: Int): Boolean {
        if (mcpath == null) return false
        val resolved = FunctionPathRegistry.getPath(mcpath)
        if (resolved.isEmpty) return false
        val key = normalizePath(resolved.get())
        return breakpoints[key]?.breakpoints?.containsKey(line) ?: false
    }

    /** The unique ID of the breakpoint at [mcpath]:[line], or empty. */
    @JvmStatic
    fun getBreakpointId(mcpath: String?, line: Int): Optional<Int> {
        if (mcpath == null) return Optional.empty()
        return FunctionPathRegistry.getPath(mcpath)
            .map(::normalizePath)
            .map(breakpoints::get)
            .flatMap { bps -> Optional.ofNullable(bps?.breakpoints?.get(line)) }
            .map { it.id }
    }

    /** Removes all breakpoints and resets the ID counter. */
    @JvmStatic
    fun clear() {
        breakpoints.clear()
        nextId = 0
    }

    /** Converts a filesystem path to a `namespace:path` MC-path, or `null`. */
    @JvmStatic
    fun fileToMcPath(path: String?): String? {
        if (path == null) return null
        val realPath = FilenameUtils.separatorsToUnix(path)
        val matcher = PATH_PATTERN.matcher(realPath)
        if (matcher.find()) {
            val namespace = matcher.group("namespace")
            val rpath = matcher.group("path")
            if (namespace != null && rpath != null) {
                return "$namespace:$rpath"
            }
        }
        return null
    }
}
