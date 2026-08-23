package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Resumes execution until the next breakpoint, or until the running function ends.
 *
 * @author theogiraudet
 */
data object ContinueInput : IInput
