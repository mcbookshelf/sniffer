package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.nbt.Tag

/**
 * Result of a single variable lookup.
 *
 * @property key name of the requested variable
 * @property value its NBT value, `null` when no execution is in progress
 * @property isMacro whether the running function is a macro, only meaningful alongside a [value]
 * @property error what went wrong, `null` when nothing did
 * @author theogiraudet
 */
data class VariableOutput(
    val key: String,
    val value: Tag? = null,
    val isMacro: Boolean = false,
    val error: String? = null,
) : Output
