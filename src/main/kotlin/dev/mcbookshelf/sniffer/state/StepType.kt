package dev.mcbookshelf.sniffer.state

/**
 * Represents the different types of debugging steps supported by the Datapack Debugger.
 * This enum defines the behavior of the debugger when stepping through code.
 */
enum class StepType {
    /** Step into — follows function calls. */
    STEP_IN,
    /** Step over — treats function calls as single steps. */
    STEP_OVER,
    /** Step out — continues until the current function returns. */
    STEP_OUT
}
