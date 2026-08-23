package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.network.chat.Component

/**
 * Result of a call stack request.
 *
 * @property stack the formatted stack, ready to display
 * @author theogiraudet
 */
data class StackOutput(val stack: Component) : Output
