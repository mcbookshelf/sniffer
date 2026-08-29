package dev.mcbookshelf.sniffer.features.breakpoints

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Triggers a breakpoint at the current position, which is what `/breakpoint` does.
 *
 * @author theogiraudet
 */
data object TriggerBreakpointInput : IInput
