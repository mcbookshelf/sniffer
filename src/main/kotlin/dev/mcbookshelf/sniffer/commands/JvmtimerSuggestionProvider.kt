package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import java.util.concurrent.CompletableFuture

/**
 * Suggests existing timer IDs for `/jvmtimer` command tab-completion.
 */
object JvmtimerSuggestionProvider: SuggestionProvider<CommandSourceStack> {
    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        JvmtimerCommand.timers.keys.forEach {
            builder.suggest(it)
        }
        return builder.buildFuture()
    }
}