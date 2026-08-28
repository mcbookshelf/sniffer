package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Arms a pause on the next debuggable line, wherever execution happens to be.
 * Nothing is running most of the time, so the request stays armed until a function does run.
 *
 * @author theogiraudet
 */
data object PauseInput : IInput
