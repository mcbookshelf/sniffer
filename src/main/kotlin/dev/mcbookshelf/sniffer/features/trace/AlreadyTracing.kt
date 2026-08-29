package dev.mcbookshelf.sniffer.features.trace

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * The trace was refused because one is already running, and a single one runs at a time.
 * The command it was given was not executed.
 *
 * @author theogiraudet
 */
object AlreadyTracing : Output
