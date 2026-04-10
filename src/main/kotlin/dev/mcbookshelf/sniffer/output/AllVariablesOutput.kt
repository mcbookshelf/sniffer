package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.nbt.Tag

/**
 * Result of an all-variables lookup.
 *
 * @property value compound NBT containing all variables, or `null` if no
 *   execution context is available.
 * @property error if non-null, an error occurred during retrieval.
 */
data class AllVariablesOutput(
    val value: Tag? = null,
    val error: String? = null,
) : Output
