package dev.mcbookshelf.sniffer.features.callstack

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor
import net.minecraft.util.CommonColors

/**
 * Renders the debug call stack as a chat [Component], for `/breakpoint stack` and for a failed assertion.
 *
 * @author theogiraudet
 */
object StackFormatter {

    private val ERROR_COLOR: Int = TextColor.parseColor("#E4514C").orThrow.value

    /** Full stack, topmost frame highlighted in diamond. */
    @JvmStatic
    fun stack(): Component = render(maxStack = Int.MAX_VALUE, color = CommonColors.WHITE, boldTop = true)

    /** Stack truncated to [maxStack] frames with a `... (N more)` suffix. */
    @JvmStatic
    fun stack(maxStack: Int): MutableComponent =
        render(maxStack = maxStack, color = CommonColors.WHITE, boldTop = true, highlightColor = CommonColors.HIGH_CONTRAST_DIAMOND)

    /** Stack in the error color, truncated to [maxStack] frames, topmost bold. */
    @JvmStatic
    fun errorStack(maxStack: Int): MutableComponent =
        render(maxStack = maxStack, color = ERROR_COLOR, boldTop = true, highlightColor = ERROR_COLOR)

    /** Full stack in the error color, topmost bold. */
    @JvmStatic
    @Suppress("unused")
    fun errorStack(): Component =
        render(maxStack = Int.MAX_VALUE, color = ERROR_COLOR, boldTop = true, highlightColor = ERROR_COLOR)

    private fun render(
        maxStack: Int,
        color: Int,
        boldTop: Boolean,
        highlightColor: Int = color,
    ): MutableComponent {
        var text: MutableComponent = Component.literal("\nCall stack:\n").withColor(color)
        val stacks = ScopeManager.get().debugScopes
        for ((count, stack) in stacks.withIndex()) {
            if (count >= maxStack) {
                text.append(Component.literal("... (${stacks.size - count} more)").withColor(color))
                break
            }
            val lineStr = if (stack.line >= 0) ":${stack.line + 1}" else ""
            val t = Component.literal("${stack.function}$lineStr")
            val isTop = stacks.indexOf(stack) == 0
            t.style = t.style
                .withBold(boldTop && isTop)
                .withColor(if (isTop) highlightColor else color)
            text = text.append(t)
            if (stacks.last() != stack) text.append("\n")
        }
        return text
    }
}
