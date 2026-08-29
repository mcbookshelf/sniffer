package dev.mcbookshelf.sniffer.features.source

/**
 * A line of a `.mcfunction` file, which the debugger and an editor do not number the same way.
 *
 * The debugger counts from zero, the way the mixin layer reads a function it has just parsed,
 * while the Debug Adapter Protocol counts from one.
 * Carrying the line as a value rather than an `Int` is what keeps the two from being confused:
 * neither number can be read without naming which of the two it is.
 *
 * A [Line] is always a real line. A function that has not run one is a `null` line, never a
 * line holding a sentinel.
 *
 * @author theogiraudet
 */
@JvmInline
value class Line private constructor(private val zeroBased: Int) {

    /** Zero indexed, the way the debugger and the mixin layer count. */
    val inFile: Int get() = zeroBased

    /** One indexed, the way an editor counts. */
    val inEditor: Int get() = zeroBased + 1

    override fun toString(): String = inEditor.toString()

    companion object {

        fun inFile(zeroBased: Int) = Line(zeroBased)

        fun inEditor(oneBased: Int) = Line(oneBased - 1)
    }
}
