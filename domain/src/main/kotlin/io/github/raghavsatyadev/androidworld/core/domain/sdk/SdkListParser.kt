package io.github.raghavsatyadev.androidworld.core.domain.sdk

/** Pure-Kotlin parser for the output of Google's `android sdk list --all` and installation commands. */
object SdkListParser {
    private val ansiRegex = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val runOfSpaces = Regex(" {2,}")
    private val channels = setOf("beta", "canary", "dev", "stable")

    /** Strips ANSI escape sequences from [text]. */
    fun strip(text: String): String = ansiRegex.replace(text, "")

    /**
     * Parses the installed and available packages from `android sdk list --all` output.
     * Escape sequences are stripped; sections are split on their respective headers.
     */
    fun parse(text: String): SdkListing {
        val stripped = strip(text)
        val installed = mutableListOf<SdkPackage>()
        val available = mutableListOf<SdkPackage>()
        var currentSection: Section? = null

        for (rawLine in stripped.lines()) {
            val line = rawLine.trimEnd('\r', ' ')
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed == "Installed packages:") {
                currentSection = Section.Installed
                continue
            }
            if (trimmed == "Available packages:") {
                currentSection = Section.Available
                continue
            }
            if (currentSection == null) continue
            if (trimmed == "(no installed packages)") continue
            if (!line.startsWith("  ")) continue

            val cells = trimmed.split(runOfSpaces)
            if (cells.size < 2) continue

            val path = cells[0]
            var idx = 1
            var channel: String? = null
            if (idx < cells.size && cells[idx].lowercase() in channels) {
                channel = cells[idx]
                idx++
            }
            if (idx >= cells.size) continue
            val version = cells[idx]
            idx++

            var update: String? = null
            if (idx < cells.size && cells[idx] == "->") {
                idx++
                if (idx < cells.size) {
                    update = cells[idx]
                    idx++
                }
            }

            val description = if (idx < cells.size) {
                cells.subList(idx, cells.size).joinToString(" ")
            } else {
                ""
            }

            val pkg = SdkPackage(
                path = path,
                version = version,
                description = description,
                channel = channel,
                update = update,
            )

            when (currentSection) {
                Section.Installed -> installed.add(pkg)
                Section.Available -> available.add(pkg)
            }
        }

        return SdkListing(installed = installed, available = available)
    }

    /**
     * Inspects output from `android sdk install|remove` for failure messages that exited with 0.
     * Returns the failure message line, or null if no failure was detected.
     */
    fun failure(output: String): String? {
        val stripped = strip(output)
        for (rawLine in stripped.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("openjdk", ignoreCase = true)) continue
            if (
                line.contains("not found", ignoreCase = true) ||
                line.startsWith("none of", ignoreCase = true) ||
                line.startsWith("error", ignoreCase = true) ||
                line.startsWith("failed", ignoreCase = true)
            ) {
                return line
            }
        }
        return null
    }

    private enum class Section {
        Installed,
        Available,
    }
}
