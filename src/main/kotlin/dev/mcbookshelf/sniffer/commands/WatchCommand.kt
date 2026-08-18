package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.logging.LogUtils
import io.methvin.watcher.DirectoryChangeEvent
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import dev.mcbookshelf.sniffer.accessor.CommandFunctionUniqueAccessors
import dev.mcbookshelf.sniffer.mixin.ServerFunctionLibraryAccessors
import dev.mcbookshelf.sniffer.mixin.ServerFunctionManagerAccessors
import dev.mcbookshelf.sniffer.watcher.WatcherManager
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.functions.CommandFunction
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionManager
import net.minecraft.util.CommonColors
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers the `/watch` command for hot-reloading datapack functions.
 *
 * Supports `start`/`stop` to begin or end file watching on a datapack,
 * `reload` to manually apply pending changes, and `auto` to toggle
 * automatic reload on file change via [WatcherManager].
 */
object WatchCommand {

    private enum class State { CREATED, MODIFIED, DELETED }

    private data class Entry(val state: State, val datapack: Path)


    private val LOGGER = LogUtils.getLogger()

    private val map = ConcurrentHashMap<Path, Entry>()

    private var isAutoReload = false

    private const val CREATED_COLOR = "#12B617"
    private const val MODIFIED_COLOR = "#D1A21E"
    private const val DELETED_COLOR = "#B61212"

    @JvmStatic
    fun onInitialize(){
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal<CommandSourceStack>("watch")
                    .requires{it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}
                    .then(literal<CommandSourceStack>("start")
                        .then(argument("id", StringArgumentType.string())
                            .suggests(DatapackIDSuggestionProvider)
                            .executes {
                                val id = StringArgumentType.getString(it ,"id")
                                val src = it.source
                                val server = src.server
                                return@executes startWatch(server, src, id)
                            }
                        )
                    ).then(literal<CommandSourceStack>("stop")
                        .then(argument("id", StringArgumentType.string())
                            .suggests(DatapackIDSuggestionProvider)
                            .executes {
                                val id = StringArgumentType.getString(it ,"id")
                                val src = it.source
                                return@executes stopWatch(src, id)
                            }
                        )
                    ).then(literal<CommandSourceStack>("auto")
                        .then(argument("bool", BoolArgumentType.bool())
                            .executes {
                                //set if watcher will auto reload function when changed
                                val bool = BoolArgumentType.getBool(it, "bool")
                                isAutoReload = bool
                                if(isAutoReload){
                                    it.source.sendSuccess({ Component.translatable("sniffer.commands.watcher.auto.enable")}, false)
                                }else{
                                    it.source.sendSuccess({ Component.translatable("sniffer.commands.watcher.auto.disable")}, false)
                                }
                                return@executes 1
                            }
                        ).executes {
                            //return if auto reload is enabled
                            it.source.sendSuccess({ Component.translatable("sniffer.commands.watcher.auto", isAutoReload) }, false)
                            return@executes 1
                        }
                    ).then(literal<CommandSourceStack>("reload")
                        .executes {
                            it.source.sendSuccess({ Component.translatable("sniffer.commands.watcher.hot_reload")}, false)
                            hotReload(it.source.server)
                            return@executes 1
                        }
                    )

            )
        }
    }

    private fun startWatch(server: MinecraftServer, src: CommandSourceStack, id: String): Int{
        try{
            val datapackPath = server.getWorldPath(LevelResource.DATAPACK_DIR)
            val packPath = datapackPath.resolve(id)
            if(Files.notExists(packPath)){
                src.sendFailure(Component.translatable("sniffer.commands.watcher.failed.datapack_not_found", id))
                return 0
            }
            val functionsRoot = packPath.resolve("data")
            val ok = WatcherManager.start(id, functionsRoot, server){
                server.execute {
//                    val msg = java.lang.String.format("[watch:%s] %s %s", id, it.eventType(), it.path())
//                    server.playerManager.broadcast(Component.of(msg), false)
                    processFunctionChange(it, packPath)
                    if(isAutoReload){
                        hotReload(server)
                    }
                }
            }
            if(ok){
                src.sendSuccess({ Component.translatable("sniffer.commands.watcher.start", id) }, false)
                return 1
            }else{
                src.sendFailure(Component.translatable("sniffer.commands.watcher.start.failed", id))
                return 0
            }
        }catch (ex: Exception){
            src.sendFailure(Component.translatable("sniffer.commands.watcher.start.failed", id))
            LOGGER.error("Failed to start watching: $id", ex)
            return 0
        }
    }

    private fun stopWatch(src: CommandSourceStack, id: String): Int{
        try{
            val ok = WatcherManager.stop(id)
            if(ok){
                src.sendSuccess({ Component.translatable("sniffer.commands.watcher.stop", id) }, false)
                return 1
            }else{
                src.sendFailure(Component.translatable("sniffer.commands.watcher.stop.failed", id))
                return 0
            }
        }catch (ex: Exception){
            src.sendFailure(Component.translatable("sniffer.commands.watcher.stop.failed", id))
            LOGGER.error("Failed to stop watching: $id", ex)
            return 0
        }
    }

    private fun processFunctionChange(event: DirectoryChangeEvent, datapackPath: Path) {
        val p = event.path()
        when (event.eventType()) {
            DirectoryChangeEvent.EventType.CREATE -> map.compute(p) { _, old ->
                when (old?.state) {
                    null -> Entry(State.CREATED, datapackPath)
                    State.CREATED -> Entry(State.CREATED, datapackPath)         // keep created
                    State.MODIFIED -> Entry(State.MODIFIED, datapackPath)     // keep modified
                    State.DELETED -> Entry(State.MODIFIED, datapackPath)      // deleted -> recreated => treated as modification
                }
            }

            DirectoryChangeEvent.EventType.MODIFY -> map.compute(p) { _, old ->
                when (old?.state) {
                    null -> Entry(State.MODIFIED, datapackPath)
                    State.CREATED -> Entry(State.CREATED, datapackPath)        // created stays created
                    State.MODIFIED -> Entry(State.MODIFIED, datapackPath)
                    State.DELETED -> Entry(State.MODIFIED, datapackPath)      // deleted -> re-appeared => modification
                }
            }

            DirectoryChangeEvent.EventType.DELETE -> map.compute(p) { _, old ->
                when (old?.state) {
                    null -> Entry(State.DELETED, datapackPath)
                    State.CREATED -> null                                     // created then deleted => remove (no-op)
                    State.MODIFIED -> Entry(State.DELETED, datapackPath)
                    State.DELETED -> Entry(State.DELETED, datapackPath)      // keep deleted
                }
            }

            else -> {
                LOGGER.error("Unknown event type: ${event.eventType()}")
            }
        }
    }

    // Record function change
    private fun created(): List<Pair<Path, Path>> =
        map.entries.filter { it.value.state == State.CREATED }.map { it.key to it.value.datapack }

    private fun modified(): List<Pair<Path, Path>> =
        map.entries.filter { it.value.state == State.MODIFIED }.map { it.key to it.value.datapack }

    private fun deleted(): List<Pair<Path, Path>> =
        map.entries.filter { it.value.state == State.DELETED }.map { it.key to it.value.datapack }

    /**
     * Applies every change seen since the last reload, as a single replacement of the function map.
     *
     * Creations, modifications and deletions used to be applied by three independent tasks, each of which read the map, waited for its own parse, then wrote its own result back.
     * Whichever finished last overwrote the other two, so a reload carrying one kind of change could be undone by the two empty ones running beside it, and the map was written from a worker thread while the server read it.
     * Parsing still happens off the server thread; the map is read and written once, on the server thread, once the parsing is done.
     */
    private fun hotReload(server: MinecraftServer) {
        val created = created()
        val modified = modified()
        val deleted = deleted()
        map.clear()
        if (created.isEmpty() && modified.isEmpty() && deleted.isEmpty()) return

        val library = (server.functions as ServerFunctionManagerAccessors).getLibrary() as ServerFunctionLibraryAccessors
        val dispatcher = library.getDispatcher()
        val source = CommandSourceStack(
            CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, server.overworld(),
            library.getFunctionCompilationPermissions(), "", CommonComponents.EMPTY, server, null
        )

        CompletableFuture
            .supplyAsync {
                compile(server, created, dispatcher, source, announceFailure = true) to
                    compile(server, modified, dispatcher, source, announceFailure = false)
            }
            .whenComplete { parsed, ex ->
                if (ex != null) {
                    LOGGER.error("Failed to hot reload functions", ex)
                    server.execute {
                        server.playerList.broadcastSystemMessage(
                            Component.translatable("sniffer.commands.watcher.modify.failed.ex", ex.message ?: "unknown")
                                .withColor(CommonColors.RED),
                            false
                        )
                    }
                    return@whenComplete
                }
                server.execute { splice(server, library, parsed.first, parsed.second, deleted) }
            }
    }

    /** Puts the parsed functions into the library, on the server thread, as one write. */
    private fun splice(
        server: MinecraftServer,
        library: ServerFunctionLibraryAccessors,
        created: List<CommandFunction<CommandSourceStack>>,
        modified: List<CommandFunction<CommandSourceStack>>,
        deleted: List<Pair<Path, Path>>,
    ) {
        val functions = HashMap(library.getFunctions())
        created.forEach {
            functions[it.id()] = it
            announce(server, "+ ${it.id()}", CREATED_COLOR)
        }
        modified.forEach {
            functions[it.id()] = it
            announce(server, "• ${it.id()}", MODIFIED_COLOR)
        }
        deleted.forEach { (functionPath, datapackPath) ->
            val identifier = getIdentifier(functionPath, datapackPath)
            functions.remove(identifier)
            announce(server, "- $identifier", DELETED_COLOR)
        }
        library.setFunctions(functions)

        // A function carrying the `load` debug tag runs on reload, and only once the library holds the version that is about to run.
        modified.filter { CommandFunctionUniqueAccessors.of(it).debugTags.contains("load") }
            .forEach { server.functions.execute(it, server.functions.gameLoopSender) }
    }

    /**
     * Reads and parses each function, dropping the ones that no longer compile.
     *
     * @param announceFailure whether a function that fails to parse is reported in chat as well as logged.
     *   Only a creation is: there is no message for a modification that fails, and inventing one would need a new translation in every language the mod ships.
     */
    private fun compile(
        server: MinecraftServer,
        paths: List<Pair<Path, Path>>,
        dispatcher: CommandDispatcher<CommandSourceStack>,
        source: CommandSourceStack,
        announceFailure: Boolean,
    ): List<CommandFunction<CommandSourceStack>> = paths.mapNotNull { (functionPath, datapackPath) ->
        val identifier = getIdentifier(functionPath, datapackPath)
        try {
            CommandFunction.fromLines(identifier, dispatcher, source, Files.readAllLines(functionPath))
        } catch (ex: Exception) {
            LOGGER.error("Failed to parse function: $identifier", ex)
            if (announceFailure) {
                server.execute {
                    server.playerList.broadcastSystemMessage(
                        Component.translatable("sniffer.commands.watcher.create.failed", identifier)
                            .withColor(CommonColors.RED),
                        false
                    )
                }
            }
            null
        }
    }

    private fun announce(server: MinecraftServer, text: String, color: String) {
        server.playerList.broadcastSystemMessage(
            Component.literal(text).withColor(TextColor.parseColor(color).getOrThrow().value),
            false
        )
    }

    private fun getIdentifier(functionPath: Path, datapackPath: Path): Identifier {
        val func = functionPath.toAbsolutePath().normalize()
        val dp = datapackPath.toAbsolutePath().normalize()
        val rel = dp.relativize(func) // Assumes valid input, rel format is data/<namespace>/functions/...

        val namespace = rel.getName(1).toString()
        val funcPathPart = rel.subpath(3, rel.nameCount).toString().replace(File.separatorChar, '/')
        val pathWithoutExt = funcPathPart.removeSuffix(".mcfunction")

        return Identifier.fromNamespaceAndPath(namespace, pathWithoutExt)
    }
}