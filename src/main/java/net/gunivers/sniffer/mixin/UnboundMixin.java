package net.gunivers.sniffer.mixin;

import net.gunivers.sniffer.accessor.UnboundUniqueAccessor;
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

    @Override
    public String getSourceFunction() {
        return sourceFunction;
    }

    @Override
    public void setSourceFunction(String sourceFunction) {
        this.sourceFunction = sourceFunction;
    }

    @Override
    public int getSourceLine() {
        return sourceLine;
    }

    @Override
    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }
}

