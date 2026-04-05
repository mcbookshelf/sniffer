package dev.mcbookshelf.sniffer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.CommandDispatcher;
import dev.mcbookshelf.sniffer.accessor.CommandFunctionUniqueAccessors;
import dev.mcbookshelf.sniffer.accessor.MacroFunctionUniqueAccessor;
import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.state.FunctionTextLoader;
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
 * Wraps {@link CommandFunction#fromLines} to add debugger features without
 * replacing the entire method:
 * <ul>
 *   <li>Saves raw source in {@link FunctionTextLoader}</li>
 *   <li>Transforms {@code #!} debug-command lines into real commands</li>
 *   <li>Collects {@code #@} debug tags</li>
 *   <li>Sets {@code sourceFunction} and {@code sourceLine} on each
 *       {@link BuildContexts.Unbound} entry via accessor</li>
 * </ul>
 */
@Mixin(CommandFunction.class)
public interface FunctionParsingMixin {

    @WrapMethod(method = "fromLines")
    private static <T extends ExecutionCommandSource<T>> CommandFunction<T> wrapFromLines(
            Identifier id, CommandDispatcher<T> dispatcher, T source, List<String> lines,
            Operation<CommandFunction<T>> original
    ) {
        // 1. Save raw source for DAP source display
        FunctionTextLoader.put(id, lines);

        // 2. Preprocess: transform #! into commands, #@ into comments
        ArrayList<String> preprocessed = new ArrayList<>(lines.size());
        ArrayList<String> debugTags = new ArrayList<>();
        // Map from preprocessed-line-index -> original 0-indexed line number
        ArrayList<Integer> lineMapping = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("#!")) {
                // Debug command: strip prefix and turn into a real command
                String debugCmd = line.substring(2).stripLeading();
                preprocessed.add(debugCmd.isEmpty() ? "# empty debug command" : debugCmd);
            } else if (line.startsWith("#@")) {
                // Debug tag: collect and replace with a comment so vanilla skips it
                String tag = line.substring(2).stripLeading().split("\\s+")[0];
                if (!tag.isEmpty()) debugTags.add(tag);
                preprocessed.add("# debug tag");
            } else {
                preprocessed.add(lines.get(i)); // keep original (untrimmed) for vanilla
            }
        }

        // 3. Build the line-number mapping before calling vanilla
        buildLineMapping(preprocessed, lineMapping);

        // 4. Call vanilla parsing with preprocessed lines
        CommandFunction<T> result = original.call(id, dispatcher, source, preprocessed);

        // 5. Post-process: set source info on each Unbound entry
        setSourceInfo(result, id.toString(), lineMapping);

        // 6. Store debug tags
        if (!debugTags.isEmpty()) {
            CommandFunctionUniqueAccessors.of(result).setDebugTags(debugTags);
        }

        return result;
    }

    /**
     * Builds a mapping from entry index to original 0-indexed line number by
     * replicating the line-classification logic of {@code fromLines}.
     */
    private static void buildLineMapping(List<String> lines, ArrayList<Integer> mapping) {
        int i = 0;
        while (i < lines.size()) {
            String trimmed = lines.get(i).trim();

            // Handle line continuation: skip continuation lines
            if (endsWithBackslash(trimmed)) {
                StringBuilder sb = new StringBuilder(trimmed);
                int startLine = i;
                while (endsWithBackslash(sb) && i + 1 < lines.size()) {
                    sb.deleteCharAt(sb.length() - 1);
                    i++;
                    sb.append(lines.get(i).trim());
                }
                String merged = sb.toString();
                // If this merged line is a command, use the start line
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
            // $-prefixed = macro, non-$ = command — both produce entries
            mapping.add(i);
            i++;
        }
    }

    private static boolean endsWithBackslash(CharSequence s) {
        return s.length() > 0 && s.charAt(s.length() - 1) == '\\';
    }

    /**
     * Sets {@code sourceFunction} and {@code sourceLine} on each entry in the
     * parsed function. Only sets on {@link BuildContexts.Unbound} entries.
     */
    @SuppressWarnings("unchecked")
    private static <T> void setSourceInfo(CommandFunction<T> function, String functionId, List<Integer> lineMapping) {
        if (function instanceof PlainTextFunction<T> plainText) {
            List<UnboundEntryAction<T>> entries = plainText.entries();
            for (int i = 0; i < entries.size(); i++) {
                setEntrySourceInfo(entries.get(i), functionId, i < lineMapping.size() ? lineMapping.get(i) : -1);
            }
        }
        // MacroFunction entries are instantiated at runtime — store the line
        // mapping so MacroInstantiationMixin can set source info on each
        // instantiated entry when the macro is called.
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
