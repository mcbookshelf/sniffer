package dev.mcbookshelf.sniffer.features.breakpoints

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import java.util.concurrent.CompletableFuture

/**
 * Completes a breakpoint condition as the server's own command tree, the way `/execute run` completes what follows it.
 *
 * The whole input is reparsed from where the condition starts rather than the condition alone,
 * so the ranges brigadier hands back are already absolute and need no shifting.
 *
 * @author theogiraudet
 */
object ConditionSuggestionProvider : SuggestionProvider<CommandSourceStack> {

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val dispatcher = context.source.server.commands.dispatcher
        val reader = StringReader(builder.input)
        reader.cursor = builder.start
        return dispatcher.getCompletionSuggestions(dispatcher.parse(reader, context.source))
    }
}
