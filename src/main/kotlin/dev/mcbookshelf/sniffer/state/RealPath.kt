package dev.mcbookshelf.sniffer.state

/**
 * Physical filesystem path of a loaded `.mcfunction` resource,
 * together with the kind of pack it came from (directory or ZIP).
 *
 * @author theogiraudet
 */
data class RealPath(val path: String, val kind: Kind) {
    enum class Kind {
        ZIP,
        DIRECTORY
    }
}
