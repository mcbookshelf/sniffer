package dev.mcbookshelf.sniffer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.CommandDispatcher;
import dev.mcbookshelf.sniffer.accessor.CommandFunctionUniqueAccessors;
import dev.mcbookshelf.sniffer.accessor.MacroFunctionUniqueAccessor;
import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.features.source.FunctionTextLoader;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps {@link CommandFunction#fromLines} so that a function carries what the debugger needs.
 * It keeps the raw source, rewrites the {@code #!} and {@code #@} directives into something vanilla accepts,
 * and attaches the source location to every parsed entry.
 *
 * @author theogiraudet
 * @author Alumopper
 */
@Mixin(CommandFunction.class)
public interface FunctionParsingMixin {

    @WrapMethod(method = "fromLines")
    private static <T extends ExecutionCommandSource<T>> CommandFunction<T> wrapFromLines(
            Identifier id, CommandDispatcher<T> dispatcher, T source, List<String> lines,
            Operation<CommandFunction<T>> original
    ) {
        FunctionTextLoader.put(id, lines);

        ArrayList<String> preprocessed = new ArrayList<>(lines.size());
        ArrayList<String> debugTags = new ArrayList<>();
        // Index in the preprocessed lines to zero indexed line number in the original source.
        ArrayList<Integer> lineMapping = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("#!")) {
                String debugCmd = line.substring(2).stripLeading();
                preprocessed.add(debugCmd.isEmpty() ? "# empty debug command" : debugCmd);
            } else if (line.startsWith("#@")) {
                // Replaced by a comment so vanilla skips the line it was on.
                String tag = line.substring(2).stripLeading().split("\\s+")[0];
                if (!tag.isEmpty()) debugTags.add(tag);
                preprocessed.add("# debug tag");
            } else {
                preprocessed.add(lines.get(i)); // Untrimmed, as vanilla expects it.
            }
        }

        buildLineMapping(preprocessed, lineMapping);
        CommandFunction<T> result = original.call(id, dispatcher, source, preprocessed);
        setSourceInfo(result, id.toString(), lineMapping);

        if (!debugTags.isEmpty()) {
            CommandFunctionUniqueAccessors.of(result).setDebugTags(debugTags);
        }

        return result;
    }

    /**
     * Maps the index of a parsed entry to the line it came from, by replaying how {@code fromLines} classifies lines.
     *
     * @param lines the preprocessed lines about to be handed to vanilla
     * @param mapping filled with one zero indexed line number per entry vanilla will produce
     */
    private static void buildLineMapping(List<String> lines, ArrayList<Integer> mapping) {
        int i = 0;
        while (i < lines.size()) {
            String trimmed = lines.get(i).trim();

            // A line ending with a backslash swallows the ones after it, and reports as the line it started on.
            if (endsWithBackslash(trimmed)) {
                StringBuilder sb = new StringBuilder(trimmed);
                int startLine = i;
                while (endsWithBackslash(sb) && i + 1 < lines.size()) {
                    sb.deleteCharAt(sb.length() - 1);
                    i++;
                    sb.append(lines.get(i).trim());
                }
                String merged = sb.toString();
                if (!merged.isEmpty() && !merged.startsWith("#")) {
                    mapping.add(startLine);
                }
                i++;
                continue;
            }

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i++;
                continue;
            }
            // Macro lines and plain command lines both produce exactly one entry.
            mapping.add(i);
            i++;
        }
    }

    private static boolean endsWithBackslash(CharSequence s) {
        return s.length() > 0 && s.charAt(s.length() - 1) == '\\';
    }

    /** Attaches the source location to every {@link BuildContexts.Unbound} entry of the parsed function. */
    @SuppressWarnings("unchecked")
    private static <T> void setSourceInfo(CommandFunction<T> function, String functionId, List<Integer> lineMapping) {
        if (function instanceof PlainTextFunction<T> plainText) {
            List<UnboundEntryAction<T>> entries = plainText.entries();
            for (int i = 0; i < entries.size(); i++) {
                setEntrySourceInfo(entries.get(i), functionId, i < lineMapping.size() ? lineMapping.get(i) : -1);
            }
        }
        // Macro entries only exist once the macro is called, so the mapping is stored for MacroInstantiationMixin.
        if (function instanceof MacroFunction<?> macroFunc) {
            MacroFunctionUniqueAccessor.of(macroFunc).setLineMapping(lineMapping);
        }
    }

    private static <T> void setEntrySourceInfo(UnboundEntryAction<T> entry, String functionId, int line) {
        if (entry instanceof BuildContexts.Unbound<?>) {
            UnboundUniqueAccessor accessor = UnboundUniqueAccessor.of((BuildContexts.Unbound<?>) entry);
            accessor.setSourceFunction(functionId);
            accessor.setSourceLine(line);
        }
    }
}
