package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.output.StackOutput
import dev.mcbookshelf.sniffer.input.GetStackInput
import dev.mcbookshelf.sniffer.ui.StackFormatter

/**
 * Returns the current debug call stack as a formatted [StackOutput].
 */
class GetStackHandler : Handler<GetStackInput> {

    override val inputType = GetStackInput::class

    override fun handle(input: GetStackInput, ctx: Context): Output =
        StackOutput(stack = StackFormatter.stack())
}
