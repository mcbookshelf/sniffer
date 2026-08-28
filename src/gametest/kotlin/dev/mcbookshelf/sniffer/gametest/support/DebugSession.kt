package dev.mcbookshelf.sniffer.gametest.support

import dev.mcbookshelf.sniffer.accessor.CommandFunctionUniqueAccessors
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.IInput
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.features.stepping.ContinueInput
import dev.mcbookshelf.sniffer.features.stepping.StepInInput
import dev.mcbookshelf.sniffer.features.stepping.StepOutInput
import dev.mcbookshelf.sniffer.features.stepping.StepOverInput
import dev.mcbookshelf.sniffer.features.breakpoints.BreakpointManager
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.features.stepping.SteppingState
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.PermissionSet
import java.nio.file.Path

/**
 * Drives the real debugger and reads back what it did.
 *
 * Everything here goes through the running server: a breakpoint is registered by the path of the function file, commands are run against the server's own brigadier tree, and stepping is dispatched through [SnifferDispatcher] exactly as an entrypoint would.
 * Nothing is stubbed, so what these methods observe is the debugger's own state.
 *
 * Progress is read from the `sniffer_test:log` storage, which the fixture functions in `src/gametest/resources/data/sniffer_test` write a marker into line by line, making "how far did execution get" a plain storage read.
 * The storage is emptied on construction, so each run starts from a blank one.
 */
class DebugSession(val helper: GameTestHelper) {

    val server: MinecraftServer = helper.level.server
    private val source = server.createCommandSourceStack()

    private val messages = mutableListOf<Component>()

    /** Keeps whatever a command writes back, instead of dropping it into the log. */
    private val recordingSource: CommandSourceStack = source.withSource(object : CommandSource {
        override fun sendSystemMessage(message: Component) {
            messages.add(message)
        }

        override fun acceptsSuccess() = true

        override fun acceptsFailure() = true

        override fun shouldInformAdmins() = false
    })

    init {
        SteppingState.resetAll()
        ScopeManager.get().clear()
        BreakpointManager.clear()
        server.commandStorage.set(LOG, CompoundTag())
    }

    /**
     * Registers a breakpoint on [line] (0-indexed, as written in the file) of [function].
     *
     * Breakpoints are keyed by the file's own path, exactly as the editor sends it, so the file is located on the classpath rather than through [dev.mcbookshelf.sniffer.state.FunctionPathRegistry] (which only tracks directory and zip packs, not the datapack bundled in this test mod).
     */
    fun breakpointAt(function: String, line: Int) = breakpointAtFile(filePath(function), line)

    /**
     * Registers a breakpoint on [line] of the file at [path], as an editor would.
     * For functions this test mod does not ship itself (a datapack written into the world, zipped or not), [path] is what the debugger resolved for them.
     */
    fun breakpointAtFile(path: String, line: Int) {
        BreakpointManager.addBreakpoint(path, line).orElseThrow {
            GameTestAssertException(Component.literal("Could not place a breakpoint in $path at line $line"), 0)
        }
    }

    /** The on-disk path of [function], as an editor would send it over DAP. */
    fun filePath(function: String): String {
        val (namespace, path) = function.split(":", limit = 2)
        val resource = "data/$namespace/function/$path.mcfunction"
        val url = javaClass.classLoader.getResource(resource) ?: fail("No such function file: $resource")
        return Path.of(url.toURI()).toString()
    }

    fun run(command: String) {
        server.commands.performPrefixedCommand(source, command)
    }

    /**
     * Runs [command] and returns everything it wrote back to its source, as text.
     *
     * Refusals come back the same way successes do: brigadier reports a failed `requires` check, a syntax error and a handler's own `sendFailure` all through [CommandSourceStack.sendFailure], so one recorder catches them all.
     *
     * @param permissions the permission set to run under, defaulting to the server's own (which passes every check).
     */
    fun runCapturing(command: String, permissions: PermissionSet? = null): List<String> {
        messages.clear()
        val target = if (permissions == null) recordingSource else recordingSource.withPermission(permissions)
        server.commands.performPrefixedCommand(target, command)
        return messages.map { it.string }
    }

    fun continueExecution() = dispatch(ContinueInput)

    fun stepIn() = dispatch(StepInInput(1))

    fun stepOver() = dispatch(StepOverInput(1))

    fun stepOut() = dispatch(StepOutInput(1))

    val isPaused: Boolean get() = SteppingState.isDebugging

    /** Markers written so far, in the order the functions declare them. */
    fun executed(): Set<String> = server.commandStorage.get(LOG).keySet()

    /** Value stored under [key] in the test log storage, or null. */
    fun stored(key: String): Int? {
        val log = server.commandStorage.get(LOG)
        return if (log.contains(key)) log.getInt(key).orElse(null) else null
    }

    /** Empties the test log storage, so a second run of a function can be read on its own. */
    fun clearLog() = server.commandStorage.set(LOG, CompoundTag())

    /** Removes [key] from the test log storage. */
    fun clearStored(key: String) {
        val log = server.commandStorage.get(LOG)
        log.remove(key)
        server.commandStorage.set(LOG, log)
    }

    /** Whether [function] survived datapack loading. */
    fun isLoaded(function: String): Boolean = server.functions.get(Identifier.parse(function)).isPresent

    /** The `#@` debug tags collected for [function] while it was parsed. */
    fun debugTags(function: String): List<String> {
        val id = Identifier.parse(function)
        val loaded = server.functions.get(id).orElseThrow {
            GameTestAssertException(Component.literal("Function $function is not loaded"), 0)
        }
        return CommandFunctionUniqueAccessors.of(loaded).debugTags
    }

    /** The paused call stack, innermost frame first. */
    fun callStack(): List<String> = ScopeManager.get().debugScopes.map { it.function }

    private fun dispatch(input: IInput) {
        SnifferDispatcher.get().dispatch(input, Context(source))
    }

    private companion object {
        val LOG: Identifier = Identifier.fromNamespaceAndPath("sniffer_test", "log")
    }
}
