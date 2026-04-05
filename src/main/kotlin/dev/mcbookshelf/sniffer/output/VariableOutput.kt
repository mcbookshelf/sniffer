package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.nbt.Tag

/**
 * Result of a single-variable lookup.
 *
 * @property key the requested variable name.
 * @property value the NBT value, or `null` if no execution context is available.
 * @property isMacro whether the current context is a macro function. Only
 *   meaningful when [value] is non-null.
 * @property error if non-null, an error occurred during retrieval.
 */
data class VariableOutput(
    val key: String,
    val value: Tag? = null,
    val isMacro: Boolean = false,
    val error: String? = null,
) : Output
