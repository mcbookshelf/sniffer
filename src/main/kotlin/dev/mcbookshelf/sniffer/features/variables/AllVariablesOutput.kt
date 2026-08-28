package dev.mcbookshelf.sniffer.features.variables

import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.nbt.Tag

/**
 * Result of a lookup of every variable at once.
 *
 * @property value a compound holding them all, `null` when no execution is in progress
 * @property error what went wrong, `null` when nothing did
 * @author theogiraudet
 */
data class AllVariablesOutput(
    val value: Tag? = null,
    val error: String? = null,
) : Output
