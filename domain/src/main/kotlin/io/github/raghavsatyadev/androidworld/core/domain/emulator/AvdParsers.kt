package io.github.raghavsatyadev.androidworld.core.domain.emulator

/**
 * Parses the `android` CLI's emulator output. The CLI colours its tables even when piped, so escape
 * codes are stripped first; columns are cut at the header's offsets because an AVD name may hold spaces.
 */
object AvdListParser {
    private val ansi = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private const val HEADER_ID = "AVD ID"
    private val columns = listOf(HEADER_ID, "AVD Name", "API Level", "Status", "Serial")

    fun strip(text: String): String = ansi.replace(text, "")

    /** Rows of `android emulator list --long`. An output without the expected header yields an empty list. */
    fun parse(text: String): List<Avd> {
        val lines = strip(text).lines().map { it.trimEnd('\r') }
        val headerIndex = lines.indexOfFirst { it.trimStart().startsWith(HEADER_ID) }
        if (headerIndex < 0) return emptyList()
        val header = lines[headerIndex]
        val starts = columns.map { header.indexOf(it) }
        if (starts.any { it < 0 } || starts != starts.sorted()) return emptyList()
        return lines.drop(headerIndex + 1).filter { it.isNotBlank() }.mapNotNull { row ->
            val cells = starts.indices.map { i ->
                val from = starts[i].coerceAtMost(row.length)
                val to = (starts.getOrNull(i + 1) ?: row.length).coerceAtMost(row.length)
                row.substring(from, to.coerceAtLeast(from)).trim()
            }
            val id = cells[0].ifEmpty { return@mapNotNull null }
            Avd(
                id = id,
                name = cells[1].ifEmpty { id },
                apiLevel = cells[2],
                status = when (cells[3].lowercase()) {
                    "online" -> AvdStatus.Online
                    "offline" -> AvdStatus.Offline
                    else -> AvdStatus.Other
                },
                serial = cells[4].takeIf { it.isNotEmpty() },
            )
        }
    }

    /** `android emulator create --list-profiles`: one profile name per line. */
    fun parseProfiles(text: String): List<String> =
        strip(text).lines().map { it.trim() }.filter { it.isNotEmpty() && it.none(Char::isWhitespace) }
}

/** Reads the parts of an AVD's `config.ini` this app shows. */
object AvdConfigParser {
    fun parse(ini: String): AvdConfig {
        val values = ini.lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .toMap()
        val tag = values["tag.id"].orEmpty().lowercase()
        val device = values["hw.device.name"]?.takeIf { it.isNotEmpty() }
        val name = device.orEmpty().lowercase()
        val formFactor = when {
            "tv" in tag -> FormFactor.Tv
            "wear" in tag -> FormFactor.Wear
            "automotive" in tag -> FormFactor.Automotive
            "desktop" in tag || "desktop" in name -> FormFactor.Desktop
            "tablet" in name || name.startsWith("pixel_c") || "fold" in name -> FormFactor.Tablet
            else -> FormFactor.Phone
        }
        return AvdConfig(formFactor, device)
    }
}
