package dev.mcbookshelf.sniffer.features.stepping

/**
 * The stepping policies the debugger supports.
 *
 * @author theogiraudet
 */
enum class StepType {
    /** Follows the called functions. */
    STEP_IN,
    /** Runs each called function as a single step. */
    STEP_OVER,
    /** Continues until the running function returns. */
    STEP_OUT
}
