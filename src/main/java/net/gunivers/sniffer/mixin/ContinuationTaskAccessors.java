package net.gunivers.sniffer.mixin;

import net.minecraft.commands.execution.tasks.ContinuationTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ContinuationTask.class)
public interface ContinuationTaskAccessors {

    @Accessor("index")
    int getIndex();
}
