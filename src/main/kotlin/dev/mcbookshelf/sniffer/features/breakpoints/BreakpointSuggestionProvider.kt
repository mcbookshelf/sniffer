package dev.mcbookshelf.sniffer.features.breakpoints

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import net.minecraft.commands.CommandSourceStack
import java.util.concurrent.CompletableFuture

/**
 * Suggests the variables of the current debug scope, for `/breakpoint get`.
 *
 * @author Alumopper
 * @author theogiraudet
 */
object BreakpointSuggestionProvider : SuggestionProvider<CommandSourceStack> {

    override fun getSuggestions(c: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val manager = ScopeManager.get()
        val scope = manager.currentScope
        if (scope.isEmpty) {
            return builder.buildFuture()
        }
        return try {
            val variables = manager.getVariables(scope.get().id).orElse(emptyList())
            for (variable in variables) {
                builder.suggest(variable.name)
            }
            builder.buildFuture()
        } catch (e: Exception) {
            builder.buildFuture()
        }
    }
}
