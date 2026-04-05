package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Programmatically trigger a breakpoint at the current execution position
 * (used by the `/breakpoint` command with no subcommand).
 */
data object TriggerBreakpointInput : IInput
