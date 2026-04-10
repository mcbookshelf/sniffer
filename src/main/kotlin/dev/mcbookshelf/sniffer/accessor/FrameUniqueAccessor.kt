package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.execution.Frame
import net.minecraft.commands.functions.InstantiatedFunction

interface FrameUniqueAccessor {
    var function: InstantiatedFunction<*>?

    companion object {
        @JvmStatic
        fun of(frame: Frame): FrameUniqueAccessor = frame as FrameUniqueAccessor
    }
}
