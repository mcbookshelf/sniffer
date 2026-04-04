package net.gunivers.sniffer.mixin;

import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.functions.FunctionBuilder;
import net.minecraft.commands.functions.MacroFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Mixin for Minecraft's FunctionBuilder class.
 * This mixin adds line number tracking to function commands during function loading.
 * It injects code after macro commands are added to the function builder to associate
 * each command with its original line number in the source file.
 * 
 * @param <T> The type of command source being used
 * 
 * @author theogiraudet
 */
@Mixin(FunctionBuilder.class)
public class FunctionBuilderMixin<T extends ExecutionCommandSource<T>> {

    /**
     * The list of macro lines that make up the function.
     * This is a shadowed field from the target class and contains all commands
     * that have been parsed from the function file.
     */
    @Shadow
    private List<MacroFunction.Entry<T>> macroEntries;

    /**
     * Injects code after a macro command is added to store its line number.
     * This method uses reflection to set the line field on the macro line object,
     * allowing the debugger to know the original source line for each command.
     * 
     * @param command The command string being added
     * @param lineNum The line number in the source file
     * @param source The command source
     * @param ci The callback info
     */
    @Inject(method = "addMacro", at = @At(value = "TAIL"))
    public void addMacro(String command, int lineNum, T source, CallbackInfo ci) {
        // Get the last added macro line and set its line number
        Optional.ofNullable(macroEntries.getLast()).ifPresent(macro -> {
            try {
                var field = macro.getClass().getDeclaredField("line");
                field.setAccessible(true);
                field.set(macro, lineNum - 1);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
