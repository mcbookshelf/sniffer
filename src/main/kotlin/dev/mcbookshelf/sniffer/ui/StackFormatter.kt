package dev.mcbookshelf.sniffer.ui

import dev.mcbookshelf.sniffer.state.ScopeManager
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor
import net.minecraft.util.CommonColors

/**
 * Renders the current debug call stack (from [ScopeManager]) as a chat
 * [Component]. Used by `/breakpoint stack` and by `AssertCommand` when an
 * assertion fails.
 *
 * Lives here rather than on `BreakPointCommand` because it is a pure view
 * over [ScopeManager] and has no stepping-state coupling; putting it on
 * the brigadier command class was incidental history.
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

    /** Error-colored stack truncated to [maxStack] frames, topmost bold. */
    @JvmStatic
    fun errorStack(maxStack: Int): MutableComponent =
        render(maxStack = maxStack, color = ERROR_COLOR, boldTop = true, highlightColor = ERROR_COLOR)

    /** Full error-colored stack, topmost bold. */
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
        var text: MutableComponent = Component.empty()
        val stacks = ScopeManager.get().debugScopes
        for ((count, stack) in stacks.withIndex()) {
            if (count >= maxStack) {
                text.append(Component.literal("... (${stacks.size - count} more)").withColor(color))
                break
            }
            val t = Component.literal(stack.function)
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
