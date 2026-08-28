package dev.mcbookshelf.sniffer.features.jvmtimer

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import java.util.concurrent.CompletableFuture

/**
 * Suggests the timers `/jvmtimer` already knows about.
 *
 * @author Alumopper
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