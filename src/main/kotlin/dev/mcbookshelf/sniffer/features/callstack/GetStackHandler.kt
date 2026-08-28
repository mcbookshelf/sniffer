package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Returns the current debug call stack as a formatted [StackOutput].
 *
 * @author theogiraudet
 */
class GetStackHandler : Handler<GetStackInput> {

    override val inputType = GetStackInput::class

    override fun handle(input: GetStackInput, ctx: Context): Output =
        StackOutput(stack = StackFormatter.stack())
}
