package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Triggers a breakpoint at the current position, which is what a bare `/breakpoint` does.
 *
 * @author theogiraudet
 */
data object TriggerBreakpointInput : IInput
