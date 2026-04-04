package net.gunivers.sniffer.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.gunivers.sniffer.DatapackDebugger;
import net.gunivers.sniffer.command.FunctionTextLoader;
import net.gunivers.sniffer.util.Extension;
import net.gunivers.sniffer.util.ReflectUtil;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.FunctionBuilder;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.functions.CommandFunction.checkCommandLineLength;
import static net.minecraft.commands.functions.CommandFunction.parseCommand;

/**
 * @author theogiraudet
 */
@Mixin(CommandFunction.class)
public interface CommandFunctionMixin {

    @Shadow
    private static boolean shouldConcatenateNextLine(CharSequence string) {
        return false;
    }

    /**
     * @author theogiraudet
     * @reason to save in each command the file and source line
     */
    @Overwrite
    static <T extends ExecutionCommandSource<T>> CommandFunction<T> fromLines(ResourceLocation id, CommandDispatcher<T> dispatcher, T source, List<String> lines) {
        FunctionTextLoader.put(id, lines);
        //noinspection unchecked
        FunctionBuilder<T> functionBuilder = ReflectUtil.newInstance(FunctionBuilder.class).getData();
        ArrayList<String> debugTags = new ArrayList<>();
        for (int i = 0; i < lines.size(); ++i) {
            int j = i + 1;
            String string = lines.get(i).trim();
            String string3;
            if (shouldConcatenateNextLine(string)) {
                StringBuilder stringBuilder = new StringBuilder(string);

                do {
                    ++i;
                    if (i == lines.size()) {
                        throw new IllegalArgumentException("Line continuation at end of file");
                    }

                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                    String string2 = lines.get(i).trim();
                    stringBuilder.append(string2);
                    checkCommandLineLength(stringBuilder);
                } while (shouldConcatenateNextLine(stringBuilder));

                string3 = stringBuilder.toString();
            } else {
                string3 = string;
            }

            checkCommandLineLength(string3);
            StringReader stringReader = new StringReader(string3);
            if (stringReader.canRead() && stringReader.peek() != '#') {
                if (stringReader.peek() == '/') {
                    stringReader.skip();
                    if (stringReader.peek() == '/') {
                        throw new IllegalArgumentException("Unknown or invalid command '" + string3 + "' on line " + j + " (if you intended to make a comment, use '#' not '//')");
                    }

                    String string2 = stringReader.readUnquotedString();
                    throw new IllegalArgumentException("Unknown or invalid command '" + string3 + "' on line " + j + " (did you mean '" + string2 + "'? Do not use a preceding forwards slash.)");
                }

                if (stringReader.peek() == '$') {
                    functionBuilder.addMacro(string3.substring(1), j, source);
                } else {
                    try {
                        var action = parseCommand(dispatcher, source, stringReader);
                        ReflectUtil.invoke(action, "setSourceFunction", id.toString())
                                .onFailure(msg -> {
                                    throw new IllegalArgumentException(msg);
                                });
                        ReflectUtil.invoke(action, "setSourceLine", j - 1)
                                .onFailure(msg -> {
                                    throw new IllegalArgumentException(msg);
                                });
                        functionBuilder.addCommand(action);
                    } catch (CommandSyntaxException commandSyntaxException) {
                        throw new IllegalArgumentException("Whilst parsing command on line " + j + ": " + commandSyntaxException.getMessage());
                    }
                }
            }else if(stringReader.canRead() && Extension.test(stringReader, "#!")){
                stringReader.skip();
                stringReader.skip();
                stringReader.skipWhitespace();
                try {
                    var action = parseCommand(dispatcher, source, stringReader);
                    ReflectUtil.invoke(action, "setSourceFunction", id.toString())
                            .onFailure(msg -> {
                                throw new IllegalArgumentException(msg);
                            });
                    ReflectUtil.invoke(action, "setSourceLine", j - 1)
                            .onFailure(msg -> {
                                throw new IllegalArgumentException(msg);
                            });
                    functionBuilder.addCommand(action);
                } catch (CommandSyntaxException commandSyntaxException) {
                     DatapackDebugger.getLogger().warn("Whilst parsing debug command on line " + j + ": " + commandSyntaxException.getMessage());
                }
            }else if(stringReader.canRead() && Extension.test(stringReader, "#@")){
                stringReader.skip();
                stringReader.skip();
                stringReader.skipWhitespace();
                //读取debugTag
                debugTags.add(stringReader.readUnquotedString());
            }
        }
        var qwq = functionBuilder.build(id);
        CommandFunctionUniqueAccessors.of(qwq).setDebugTags(debugTags);
        return qwq;
    }
}
