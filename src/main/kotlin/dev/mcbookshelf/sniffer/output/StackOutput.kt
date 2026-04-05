package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.network.chat.Component

/**
 * Result of a stack-trace query.
 *
 * @property stack the formatted call-stack component, ready to display.
 */
data class StackOutput(val stack: Component) : Output
