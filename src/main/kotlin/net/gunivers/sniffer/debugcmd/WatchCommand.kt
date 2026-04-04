package net.gunivers.sniffer.debugcmd

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.logging.LogUtils
import io.methvin.watcher.DirectoryChangeEvent
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.gunivers.sniffer.mixin.CommandFunctionUniqueAccessors
import net.gunivers.sniffer.mixin.ServerFunctionLibraryAccessors
import net.gunivers.sniffer.mixin.ServerFunctionManagerAccessors
import net.gunivers.sniffer.watcher.WatcherManager
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.functions.CommandFunction
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionManager
import net.minecraft.util.CommonColors
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object WatchCommand {

    private enum class State { CREATED, MODIFIED, DELETED }

    private data class Entry(val state: State, val datapack: Path)


    private val LOGGER = LogUtils.getLogger()

    private var createdFunction = emptyList<Pair<Path, Path>>()
    private var deletedFunction = emptyList<Pair<Path, Path>>()
    private var modifiedFunction = emptyList<Pair<Path, Path>>()

    private val map = ConcurrentHashMap<Path, Entry>()

    private var isAutoReload = false

    @JvmStatic
    fun onInitialize(){
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal<CommandSourceStack?>("watch")
                    .requires{it.hasPermission(2)}
                    .then(literal<CommandSourceStack?>("start")
                        .then(argument("id", StringArgumentType.string())
                            .suggests(DatapackIDSuggestionProvider)
                            .executes {
                                val id = StringArgumentType.getString(it ,"id")
                                val src = it.source
                                val server = src.server
                                return@executes startWatch(server, src, id)
                            }
                        )
                    ).then(literal<CommandSourceStack?>("stop")
                        .then(argument("id", StringArgumentType.string())
                            .suggests(DatapackIDSuggestionProvider)
                            .executes {
                                val id = StringArgumentType.getString(it ,"id")
                                val src = it.source
                                return@executes stopWatch(src, id)
                            }
                        )
                    ).then(literal<CommandSourceStack?>("auto")
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
                    ).then(literal<CommandSourceStack?>("reload")
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

    private fun snapshotAndClear(){
        createdFunction = created()
        modifiedFunction = modified()
        deletedFunction = deleted()
        map.clear()
    }

    private fun hotReload(server: MinecraftServer){
        snapshotAndClear()
        createFunction(server, server.functions, createdFunction)
        modifyFunction(server, server.functions, modifiedFunction)
        deleteFunction(server, server.functions, deletedFunction)
    }

    private fun modifyFunction(server: MinecraftServer, manager: ServerFunctionManager, path: List<Pair<Path, Path>>){
        val la = (manager as ServerFunctionManagerAccessors).library as ServerFunctionLibraryAccessors
        val dispatcher = la.dispatcher
        val functions = la.functions
        val level = la.functionCompilationLevel
        val CommandSourceStack = CommandSourceStack(
            CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, level, "", CommonComponents.EMPTY, null, null
        )
        CompletableFuture.supplyAsync {
            path.map { (functionPath, datapackPath) ->
                val identifier = getIdentifier(functionPath, datapackPath)
                //read function contents
                val lines = Files.readAllLines(functionPath)
                try{
                    return@map CommandFunction.fromLines(identifier, dispatcher, CommandSourceStack, lines)
                }catch (ex: Exception){
                    //val text = Component.translatable("sniffer.commands.watcher.modify.failed", identifier.toString()).withColor(CommonColors.RED)
                    //server.playerList.broadcastSystemMessage(text, false)
                    LOGGER.error("Failed to modify function: $identifier", ex)
                    return@map null
                }
            }.filterNotNull()
        }.handle {modified, ex ->
            if(ex != null){
                val text = Component.translatable("sniffer.commands.watcher.modify.failed.ex", ex.message).withColor(CommonColors.RED)
                server.playerList.broadcastSystemMessage(text, false)
                LOGGER.error("Failed to modify functions", ex)
            }
            val qwq = HashMap<ResourceLocation, CommandFunction<CommandSourceStack>>()
            qwq.putAll(functions)
            modified.forEach {
                qwq[it.id()] = it
                //if a function is with "load" debug tag, execution it when hot reload
                if(CommandFunctionUniqueAccessors.of(it).debugTags.contains("load")){
                    manager.execute(it, manager.gameLoopSender)
                }
                val text = Component.literal("• ${it.id()}").withColor(TextColor.parseColor("#D1A21E").getOrThrow().value)
                server.playerList.broadcastSystemMessage(text, false)
            }
            la.functions = qwq
        }
    }

    private fun createFunction(server: MinecraftServer,  manager: ServerFunctionManager, path: List<Pair<Path, Path>>){
        val la = (manager as ServerFunctionManagerAccessors).library as ServerFunctionLibraryAccessors
        val dispatcher = la.dispatcher
        val functions = la.functions
        val level = la.functionCompilationLevel
        val CommandSourceStack = CommandSourceStack(
            CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, level, "", CommonComponents.EMPTY, null, null
        )
        CompletableFuture.supplyAsync {
            path.map { (functionPath, datapackPath) ->
                val identifier = getIdentifier(functionPath, datapackPath)
                //read function contents
                val lines = Files.readAllLines(functionPath)
                try{
                    return@map CommandFunction.fromLines(identifier, dispatcher, CommandSourceStack, lines)
                }catch (ex: Exception){
                    val text = Component.translatable("sniffer.commands.watcher.create.failed", identifier).withColor(CommonColors.RED)
                    server.playerList.broadcastSystemMessage(text, false)
                    LOGGER.error("Failed to create function: $identifier", ex)
                    return@map null
                }
            }.filterNotNull()
        }.handle {created, ex ->
            if(ex != null){
                val text = Component.translatable("sniffer.commands.watcher.create.failed.ex", ex.message).withColor(CommonColors.RED)
                server.playerList.broadcastSystemMessage(text, false)
                LOGGER.error("Failed to create functions", ex)
            }
            val qwq = HashMap<ResourceLocation, CommandFunction<CommandSourceStack>>()
            qwq.putAll(functions)
            created.forEach {
                qwq[it.id()] = it
                val text = Component.literal("+ ${it.id()}").withColor(TextColor.parseColor("#12B617").getOrThrow().value)
                server.playerList.broadcastSystemMessage(text, false)
            }
            la.functions = qwq
        }
    }

    private fun deleteFunction(server: MinecraftServer, manager:ServerFunctionManager, path: List<Pair<Path, Path>>){
        val la = (manager as ServerFunctionManagerAccessors).library as ServerFunctionLibraryAccessors
        val functions = la.functions
        CompletableFuture.supplyAsync {
            path.map { (functionPath, datapackPath) ->
                getIdentifier(functionPath, datapackPath)
            }
        }.handle {deleted, ex ->
            if(ex != null){
                val text = Component.translatable("sniffer.commands.watcher.delete.failed.ex", ex.message).withColor(CommonColors.RED)
                server.playerList.broadcastSystemMessage(text, false)
                LOGGER.error("Failed to delete functions", ex)
            }
            val qwq = HashMap<ResourceLocation, CommandFunction<CommandSourceStack>>()
            qwq.putAll(functions)
            deleted.forEach {
                qwq.remove(it)
                val text = Component.literal("- $it").withColor(TextColor.parseColor("#B61212").getOrThrow().value)
                server.playerList.broadcastSystemMessage(text, false)
            }
            la.functions = qwq
        }
    }

    private fun getIdentifier(functionPath: Path, datapackPath: Path): ResourceLocation {
        val func = functionPath.toAbsolutePath().normalize()
        val dp = datapackPath.toAbsolutePath().normalize()
        val rel = dp.relativize(func) // 假设输入合法，rel 格式为 data/<namespace>/functions/...

        val namespace = rel.getName(1).toString()
        val funcPathPart = rel.subpath(3, rel.nameCount).toString().replace(File.separatorChar, '/')
        val pathWithoutExt = funcPathPart.removeSuffix(".mcfunction")

        return ResourceLocation.fromNamespaceAndPath(namespace, pathWithoutExt)
    }
}