package net.gunivers.sniffer.debugcmd

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.level.storage.LevelResource
import java.io.File
import java.util.concurrent.CompletableFuture

object DatapackIDSuggestionProvider: SuggestionProvider<CommandSourceStack> {
    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val datapackPath = context.source.server.getWorldPath(LevelResource.DATAPACK_DIR)
        val datapackIDs = datapackPath.toFile().listFiles(File::isDirectory)?.map { it.name } ?: emptyList()
        for (datapackID in datapackIDs) {
            builder.suggest(datapackID)
        }
        return builder.buildFuture()
    }
}