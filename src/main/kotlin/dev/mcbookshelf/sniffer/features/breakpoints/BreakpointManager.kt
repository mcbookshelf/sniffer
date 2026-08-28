package dev.mcbookshelf.sniffer.features.breakpoints

import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.context.ContextChain
import com.mojang.brigadier.exceptions.CommandSyntaxException
import dev.mcbookshelf.sniffer.util.IsolatedExecution
import net.minecraft.commands.CommandResultCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.LevelBasedPermissionSet
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Paths
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager

/**
 * Owns breakpoint storage, path resolution and the "should execution pause here" query.
 *
 * @author theogiraudet
 */
object BreakpointManager {

    private val LOGGER = LoggerFactory.getLogger("sniffer")
    private val PATH_PATTERN: Pattern =
        Pattern.compile("data/(?<namespace>.+)/function/(?<path>.+)\\.mcfunction")

    private data class Breakpoint(val id: Int, val line: Int, val condition: String? = null)

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
     * Set while a condition command is running, so the mixin layer leaves the lines it executes alone.
     * Without it a condition calling a function would check breakpoints and consume step counters of its own.
     */
    @JvmField
    var evaluatingCondition: Boolean = false

    /**
     * Whether execution should stop at [mcpath]:[line].
     * A breakpoint has to be set there and the debugger must not already be paused on it,
     * which is what keeps a step onto a breakpoint line from triggering it twice.
     * Its condition, if it has one, is only run against [source] once the position matched,
     * so unconditional breakpoints pay nothing for it.
     * An evaluation error pauses anyway, since a breakpoint is never silently skipped.
     */
    @JvmStatic
    fun mustStop(mcpath: String?, line: Int, source: CommandSourceStack?): Boolean {
        if (mcpath == null) return false
        val breakpoint = byMcPath[mcpath]?.breakpoints?.get(line) ?: return false
        if (isAtCurrentPosition(mcpath, line)) return false
        return conditionHolds(breakpoint, mcpath, source)
    }

    /**
     * Runs [breakpoint]'s condition command (if any) with [source] and reports what its success channel said.
     * The breakpoint pauses when the command succeeded, exactly as `execute if` would read it.
     */
    private fun conditionHolds(breakpoint: Breakpoint, mcpath: String, source: CommandSourceStack?): Boolean {
        val condition = breakpoint.condition ?: return true
        if (source == null) {
            LOGGER.warn(
                "Cannot run condition of breakpoint at {}:{} without a command source; pausing anyway",
                mcpath, breakpoint.line,
            )
            return true
        }
        return try {
            runCondition(condition, source)
        } catch (e: Exception) {
            LOGGER.warn("Failed to run condition of breakpoint at {}:{}; pausing anyway", mcpath, breakpoint.line, e)
            true
        }
    }

    /**
     * Runs [raw] synchronously, leading slash optional, and returns whether it reported success.
     * This is what both entrypoints mean by a condition, so `/breakpoint <command>` and a DAP condition read the same.
     *
     * The command is given a silent copy of [source] so a condition in a hot function does not spam chat,
     * and a callback that catches the success channel.
     * A condition whose sources are all filtered out, `execute if` on a false test being the usual one,
     * never reaches that callback and so reads as a failure.
     *
     * Vanilla would queue the command into the execution context the paused function is running in,
     * where it would only run once that function is over, so the context is cleared for the duration of the call.
     * That is what makes the run synchronous and isolated from the debugged execution.
     *
     * @throws CommandSyntaxException if [raw] is not a runnable command.
     */
    @JvmStatic
    @Throws(CommandSyntaxException::class)
    fun runCondition(raw: String, source: CommandSourceStack): Boolean {
        val success = AtomicBoolean(false)
        val conditionSource = source.withSuppressedOutput()
            .withCallback(CommandResultCallback { ok, _ -> if (ok) success.set(true) })

        val commands = source.server.commands
        val command = Commands.trimOptionalPrefix(raw.trim())
        val parse = validateRunnable(commands.dispatcher.parse(command, conditionSource), command)

        // Restored rather than cleared, since the condition may have run a `/breakpoint` of its own.
        val outerEvaluating = evaluatingCondition
        evaluatingCondition = true
        try {
            IsolatedExecution.outsideCurrentContext { commands.performCommand(parse, command) }
        } finally {
            evaluatingCondition = outerEvaluating
        }
        return success.get()
    }

    /**
     * Checks that [raw] is a command the server can run and returns it stripped of its optional leading slash.
     * Conditions are validated when they are set rather than when they are hit,
     * so a typo comes back as an unverified breakpoint instead of a surprise at runtime.
     * They are validated at the permission level functions run at,
     * since one the debuggee cannot even parse pauses on every hit rather than being read.
     *
     * @throws CommandSyntaxException if the string is not a runnable command.
     */
    @JvmStatic
    @Throws(CommandSyntaxException::class)
    fun parseCondition(raw: String, source: CommandSourceStack): String {
        val command = Commands.trimOptionalPrefix(raw.trim())
        val functionSource = source.withPermission(LevelBasedPermissionSet.GAMEMASTER)
        validateRunnable(source.server.commands.dispatcher.parse(command, functionSource), command)
        return command
    }

    /**
     * Checks that [parse] both parses and yields something to run, and returns it.
     * A command stopping short of anything executable, `say` with no message, is not runnable either.
     *
     * @throws CommandSyntaxException if the parse is not a runnable command.
     */
    @Throws(CommandSyntaxException::class)
    private fun validateRunnable(
        parse: ParseResults<CommandSourceStack>,
        command: String,
    ): ParseResults<CommandSourceStack> {
        Commands.validateParseResults(parse)
        ContextChain.tryFlatten(parse.context.build(command)).orElseThrow {
            CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.reader)
        }
        return parse
    }

    private fun isAtCurrentPosition(file: String?, line: Int): Boolean {
        val functionName = scopeManager.currentScope.map { it.function }.orElse("")
        val functionLine = scopeManager.currentScope.map { it.line }.orElse(-1)
        return file == functionName && line == functionLine
    }

    /**
     * Registers a breakpoint at [line] in the file at [filePath].
     *
     * @param condition condition command, `null` when unconditional.
     *   The breakpoint only pauses when it succeeds, see [mustStop].
     * @return the unique id of the new breakpoint, or empty if the file is not a Minecraft function
     */
    @JvmStatic
    @JvmOverloads
    fun addBreakpoint(filePath: String?, line: Int, condition: String? = null): Optional<Int> {
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
            funBps.breakpoints[line] = Breakpoint(id, line, condition)
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
