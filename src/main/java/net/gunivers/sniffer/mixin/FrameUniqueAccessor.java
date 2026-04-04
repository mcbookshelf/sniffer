package net.gunivers.sniffer.mixin;

import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.functions.InstantiatedFunction;

public interface FrameUniqueAccessor {
    void setFunction(InstantiatedFunction<?> function);
    InstantiatedFunction<?> getFunction();

    static FrameUniqueAccessor of(Frame frame) {
        return (FrameUniqueAccessor) (Object) frame;
    }
}
