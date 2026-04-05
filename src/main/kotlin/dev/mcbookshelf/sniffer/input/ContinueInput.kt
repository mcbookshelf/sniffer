package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Resume execution from the current paused position, running until the
 * next breakpoint or the end of the program.
 */
data object ContinueInput : IInput
