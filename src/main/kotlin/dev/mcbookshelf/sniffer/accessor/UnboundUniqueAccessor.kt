package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.execution.tasks.BuildContexts

interface UnboundUniqueAccessor {
    var sourceFunction: String?
    var sourceLine: Int

    companion object {
        @JvmStatic
        fun of(action: BuildContexts.Unbound<*>): UnboundUniqueAccessor = action as UnboundUniqueAccessor
    }
}
