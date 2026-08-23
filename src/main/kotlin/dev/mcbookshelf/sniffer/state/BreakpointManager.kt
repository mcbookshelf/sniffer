package dev.mcbookshelf.sniffer.state

import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Paths
import java.util.Optional
import java.util.regex.Pattern

/**
 * Owns breakpoint storage, path resolution and the "should execution pause here" query.
 *
 * @author theogiraudet
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

    /** Primary index: normalized filesystem path → breakpoints. */
    private val byFilePath: MutableMap<String, FunctionBreakpoints> = HashMap()

    /** Secondary index: `namespace:path` function location → breakpoints. */
    private val byMcPath: MutableMap<String, FunctionBreakpoints> = HashMap()

    private var nextId: Int = 0

    private val scopeManager: ScopeManager get() = ScopeManager.get()

    /**
     * Canonicalizes a filesystem path so every lookup agrees on it.
     * [java.nio.file.Path.toRealPath] is what fixes the drive letter case on Windows,
     * where VSCode sends `e:\...` and the Minecraft path API yields `E:\...`.
     */
    private fun normalizePath(filePath: String): String {
        val p = Paths.get(filePath)
        return try {
            p.toRealPath().toString()
        } catch (_: IOException) {
            p.toAbsolutePath().normalize().toString()
        }
    }

    /**
     * Whether execution should stop at [mcpath]:[line].
     * A breakpoint has to be set there and the debugger must not already be paused on it,
     * which is what keeps a step onto a breakpoint line from triggering it twice.
     */
    @JvmStatic
    fun mustStop(mcpath: String?, line: Int): Boolean =
        contains(mcpath, line) && !isAtCurrentPosition(mcpath, line)

    private fun isAtCurrentPosition(file: String?, line: Int): Boolean {
        val functionName = scopeManager.currentScope.map { it.function }.orElse("")
        val functionLine = scopeManager.currentScope.map { it.line }.orElse(-1)
        return file == functionName && line == functionLine
    }

    /**
     * Registers a breakpoint at [line] in the file at [filePath].
     *
     * @return the unique id of the new breakpoint, or empty if the file is not a Minecraft function
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
            val funBps = byFilePath.getOrPut(normalized) { FunctionBreakpoints(mcpath, normalized) }
            byMcPath[mcpath] = funBps
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
        val removed = byFilePath.remove(normalizePath(filePath))
        if (removed != null) {
            byMcPath.remove(removed.functionMcPath)
        }
        LOGGER.debug("Cleared all breakpoints for {}", filePath)
    }

    /** Whether a breakpoint is set at [mcpath]:[line]. Hot path: no I/O. */
    @JvmStatic
    fun contains(mcpath: String?, line: Int): Boolean {
        if (mcpath == null || byMcPath.isEmpty()) return false
        return byMcPath[mcpath]?.breakpoints?.containsKey(line) ?: false
    }

    /** The unique ID of the breakpoint at [mcpath]:[line], or empty. */
    @JvmStatic
    fun getBreakpointId(mcpath: String?, line: Int): Optional<Int> {
        if (mcpath == null) return Optional.empty()
        return Optional.ofNullable(byMcPath[mcpath]?.breakpoints?.get(line)).map { it.id }
    }

    /** Removes all breakpoints and resets the ID counter. */
    @JvmStatic
    fun clear() {
        byFilePath.clear()
        byMcPath.clear()
        nextId = 0
    }

    /** Converts a filesystem path to a `namespace:path` function location, or `null` if it is not one. */
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
