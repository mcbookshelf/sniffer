package dev.mcbookshelf.sniffer.features.evaluate

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * What a command answered, as a reader of a debug console expects to see it.
 *
 * @property feedback everything the command wrote back, one entry per message, empty when it wrote nothing
 * @property success whether the command reported success on its result channel
 * @property result the value on that channel, `0` when the command never reached it
 * @author theogiraudet
 */
data class RunCommandOutput(
    val feedback: List<String>,
    val success: Boolean,
    val result: Int,
) : Output
