package io.github.raghavsatyadev.androidworld.core.domain.files

/**
 * Parses `ls -la` output from toybox (Android 6+) and the older toolbox `ls`. The regex is anchored
 * on the date and time, like ddmlib's `FileListingService.LS_L_PATTERN`, because the size column is
 * not one token for device nodes (`10, 260`) and is missing on toolbox for folders. The name is the
 * rest of the line after exactly one space, sliced from the original text, so names with runs of
 * spaces survive. ` -> ` splits name and target only for symlinks, since a regular file may contain
 * it in its name. When SELinux hides an entry from `stat`, toybox prints only its type and name with
 * `?` in every other column; those come back with unknown metadata.
 */
object LsParser {
    private val LINE = Regex(
        """^([dlcbsp-][-rwxsStTl]{9})[+.@]?\s+""" + // mode
            """(?:\d+\s+)?""" + // link count (toybox only)
            """(\S+)\s+(\S+)\s+""" + // owner, group
            """(?:(\d+)|\d+,\s*\d+)?\s*""" + // size, or major, minor
            """(\d{4}-\d\d-\d\d \d\d:\d\d(?::\d\d(?:\.\d+)?)?(?: [+-]\d{4})?|""" + // ISO date
            """[A-Z][a-z]{2} [ \d]\d (?:\d\d:\d\d| \d{4}))""" + // or `Mon DD HH:MM|YYYY`
            """ (.*)$""",
    )
    private val UNSTATED = Regex("""^([dlcbsp-])\?{9}\s+\?\s+\?\s+\?\s+\?\s+\? (.*)$""")
    private val TOTAL = Regex("""^total \d+$""")

    fun parse(output: String): RemoteListing {
        val entries = mutableListOf<RemoteEntry>()
        val errors = mutableListOf<String>()
        for (raw in output.lineSequence()) {
            val line = raw.removeSuffix("\r")
            if (line.isBlank() || TOTAL.matches(line.trim())) continue
            val entry = parseLine(line)
            when {
                entry == null -> errors += line.trim()
                entry.name == "." || entry.name == ".." -> Unit
                else -> entries += entry
            }
        }
        return RemoteListing(sort(entries), errors)
    }

    fun parseLine(line: String): RemoteEntry? {
        val match = LINE.matchEntire(line) ?: return parseUnstated(line)
        val (mode, owner, group, size, modified, rest) = match.destructured
        val type = RemoteEntryType.of(mode[0]) ?: return null
        val (name, target) = if (type == RemoteEntryType.Link && rest.contains(LINK_ARROW)) {
            rest.substringBefore(LINK_ARROW) to rest.substringAfter(LINK_ARROW)
        } else {
            rest to null
        }
        if (name.isEmpty()) return null
        return RemoteEntry(
            name = name,
            type = type,
            permissions = mode,
            owner = owner,
            group = group,
            size = size.toLongOrNull(),
            modified = modified,
            linkTarget = target,
        )
    }

    private fun parseUnstated(line: String): RemoteEntry? {
        val match = UNSTATED.matchEntire(line) ?: return null
        val (code, rest) = match.destructured
        val type = RemoteEntryType.of(code[0]) ?: return null
        val name = if (type == RemoteEntryType.Link) rest.substringBefore(LINK_ARROW) else rest
        if (name.isEmpty()) return null
        return RemoteEntry(name, type, permissions = code + "?????????", owner = "?", group = "?", size = null, modified = "", linkTarget = null)
    }

    /** Folders and folder-like links first, then by name, case-insensitively. */
    fun sort(entries: List<RemoteEntry>): List<RemoteEntry> =
        entries.sortedWith(compareBy<RemoteEntry> { !it.canOpen }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private const val LINK_ARROW = " -> "
}
