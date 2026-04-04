package net.gunivers.sniffer.mixin;

import net.minecraft.commands.execution.tasks.BuildContexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to add source-file information to each command: the source function and the line
 *
 * @author theogiraudet
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(BuildContexts.Unbound.class)
public class UnboundMixin implements UnboundUniqueAccessor {

    @Unique
    private String sourceFunction;

    @Unique
    private int sourceLine;

    @Unique
    @Override
    public String getSourceFunction() {
        return sourceFunction;
    }

    @Unique
    @Override
    public void setSourceFunction(String sourceFunction) {
        this.sourceFunction = sourceFunction;
    }

    @Unique
    @Override
    public int getSourceLine() {
        return sourceLine;
    }

    @Unique
    @Override
    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }
}

