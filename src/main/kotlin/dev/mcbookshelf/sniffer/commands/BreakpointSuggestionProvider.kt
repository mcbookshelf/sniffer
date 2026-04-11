package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.mcbookshelf.sniffer.state.ScopeManager
import net.minecraft.commands.CommandSourceStack
import java.util.concurrent.CompletableFuture

/**
 * Provides command suggestions for breakpoint-related commands.
 * Suggests available variable names from the current debug scope
 * when using the breakpoint get command.
 *
 * @author theogiraudet
 */
object BreakpointSuggestionProvider : SuggestionProvider<CommandSourceStack> {

    override fun getSuggestions(c: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val scope = ScopeManager.get().currentScope
        if (scope.isEmpty) {
            return builder.buildFuture()
        }
        return try {
            val variables = scope.get().rootVariables()
            for (variable in variables) {
                builder.suggest(variable.name)
            }
            builder.buildFuture()
        } catch (e: Exception) {
            builder.buildFuture()
        }
    }
}
