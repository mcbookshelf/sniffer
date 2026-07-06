package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.CommandFunctionUniqueAccessors;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;

/**
 * Adds the {@code debugTags} field (populated from {@code #@} directives by {@link FunctionParsingMixin}) to both concrete {@code CommandFunction} implementations.
 * Without this, the {@code CommandFunctionUniqueAccessors} cast fails with a {@link ClassCastException}.
 */
@Mixin({PlainTextFunction.class, MacroFunction.class})
public class CommandFunctionMixin implements CommandFunctionUniqueAccessors {

    @Unique
    private ArrayList<String> debugTags = new ArrayList<>();

    @Override
    public ArrayList<String> getDebugTags() {
        return debugTags;
    }

    @Override
    public void setDebugTags(ArrayList<String> debugTags) {
        this.debugTags = debugTags;
    }
}
