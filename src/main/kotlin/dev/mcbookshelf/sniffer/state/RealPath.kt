package dev.mcbookshelf.sniffer.state

/**
 * Where a loaded `.mcfunction` file lives on disk.
 *
 * @property path absolute path of the file, or of the entry inside the zip it comes from
 * @property kind what the pack holding it is
 * @author theogiraudet
 */
data class RealPath(val path: String, val kind: Kind) {
    enum class Kind {
        ZIP,
        DIRECTORY
    }
}
