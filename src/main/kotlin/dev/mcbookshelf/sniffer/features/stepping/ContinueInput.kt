package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Resumes execution until the next breakpoint, or until the running function ends.
 *
 * @author theogiraudet
 */
data object ContinueInput : IInput
