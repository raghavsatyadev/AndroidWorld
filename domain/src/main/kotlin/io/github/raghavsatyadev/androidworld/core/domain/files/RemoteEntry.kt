package io.github.raghavsatyadev.androidworld.core.domain.files

/** The kind of a device file, from the first character of its `ls -l` mode string. */
enum class RemoteEntryType(val code: Char) {
    Directory('d'),
    File('-'),
    Link('l'),
    CharDevice('c'),
    BlockDevice('b'),
    Socket('s'),
    Pipe('p'),
    ;

    companion object {
        fun of(code: Char): RemoteEntryType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One line of a device `ls -la` listing. [size] is null for device nodes (they show `major, minor`
 * instead) and on old toolbox builds that print no size for folders. [linkTarget] is set only for
 * symlinks; whether a link leads to a folder is learned by opening it.
 */
data class RemoteEntry(
    val name: String,
    val type: RemoteEntryType,
    val permissions: String,
    val owner: String,
    val group: String,
    val size: Long?,
    val modified: String,
    val linkTarget: String? = null,
) {
    val isDirectory: Boolean get() = type == RemoteEntryType.Directory
    val isLink: Boolean get() = type == RemoteEntryType.Link

    /** Folders and links can be opened; a link that turns out to be a file is then treated as one. */
    val canOpen: Boolean get() = isDirectory || isLink
}

/** The parsed listing: [entries] in folder-first, name order, and the lines ls reported as errors. */
data class RemoteListing(val entries: List<RemoteEntry>, val errors: List<String>) {
    val permissionDenied: Boolean get() = entries.isEmpty() && errors.any { it.contains("Permission denied", ignoreCase = true) }
    val notADirectory: Boolean get() = entries.isEmpty() && errors.any { it.contains("Not a directory", ignoreCase = true) }
}
