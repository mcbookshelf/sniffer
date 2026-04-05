package dev.mcbookshelf.sniffer.state

/**
 * @param isRoot Whether this variable must be displayed directly in the scope
 *               rather than as a child of another variable
 *
 * @author theogiraudet
 */
data class DebuggerVariable(
    val id: Int,
    val name: String,
    val value: String,
    val children: List<DebuggerVariable>,
    val isRoot: Boolean
)
