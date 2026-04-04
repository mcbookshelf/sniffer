package net.gunivers.sniffer.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.gunivers.sniffer.util.ReflectUtil;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides command suggestions for breakpoint-related commands.
 * This class helps users by suggesting available variable names and keys
 * when using the breakpoint get command.
 *
 * @author Alumopper
 */
public class BreakpointSuggestionProvider implements SuggestionProvider<CommandSourceStack>{

    /** Singleton instance of the suggestion provider */
    public static final BreakpointSuggestionProvider INSTANCE = new BreakpointSuggestionProvider();

    /**
     * Private constructor to enforce singleton pattern.
     */
    private BreakpointSuggestionProvider() {}

    /**
     * Generates suggestions for available keys in the current debug context.
     * @param c The command context
     * @param builder The suggestions builder
     * @return A future containing the generated suggestions
     */
    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> c, SuggestionsBuilder builder) {
        var context = BreakPointCommand.storedCommandExecutionContext.peekFirst();
        if(context == null){
            return builder.buildFuture();
        }
        try {
            var keys = (List<String>) ReflectUtil.invoke(context, "getKeys");
            for (var key : keys) {
                builder.suggest(key);
            }
            return builder.buildFuture();
        }catch (Exception e){
            return builder.buildFuture();
        }
    }
}
