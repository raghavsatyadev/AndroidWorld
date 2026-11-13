package io.github.raghavsatyadev.androidworld.core.domain.content

/** What one `content query` printed, folded into something the UI can show. */
sealed interface ContentQueryResult {
    /** Rows keyed by column name. A `NULL` cell is `null` (indistinguishable from the text "NULL"). */
    data class Rows(val columns: List<String>, val rows: List<Map<String, String?>>) : ContentQueryResult

    /** `No result found.`: the provider answered with no rows. */
    data object Empty : ContentQueryResult

    /** The shell user may not read this provider (a `SecurityException` / `Permission Denial`). */
    data class PermissionDenied(val message: String) : ContentQueryResult

    /** No provider is registered for the URI's authority on this device. */
    data class ProviderMissing(val message: String) : ContentQueryResult

    /** Any other provider error, e.g. an unknown column; [message] is the exception line. */
    data class Error(val message: String) : ContentQueryResult
}

/**
 * Parses the output of `adb shell content query`, which prints one record per cursor row as
 * `Row: N key1=value1, key2=value2, ...` with values written raw: they can hold `, `, `=` and even
 * line breaks, so splitting on commas (as most scripts do) breaks real data.
 *
 * With the projection known (every query here passes `--projection`), each value runs from its
 * `key=` to the next expected `, nextKey=`, so only a value containing that exact text is
 * ambiguous. A record starts only on a `Row: N ` line whose N is the next expected index; any
 * other line continues the previous value. `content` exits 0 even on errors and writes them to
 * stderr (older builds to stdout), so both streams are classified and the exit code is ignored.
 */
object ContentQueryParser {
    private val rowHeader = Regex("""^Row: (\d+) (.*)$""")
    private val fallbackSplit = Regex(""", (?=[A-Za-z_][A-Za-z0-9_]*=)""")

    fun parse(stdout: String, stderr: String = "", columns: List<String>? = null): ContentQueryResult {
        val out = normalize(stdout)
        val err = normalize(stderr)
        val records = records(out)
        if (records.isEmpty()) {
            errorOf("$out\n$err")?.let { return it }
            return if (out.isBlank() || out.trim().startsWith("No result found")) ContentQueryResult.Empty
            else ContentQueryResult.Error(out.trim().lineSequence().first())
        }
        val keys = columns?.takeIf { it.isNotEmpty() }
        return if (keys != null) {
            ContentQueryResult.Rows(keys, records.map { splitKnown(it, keys) })
        } else {
            val rows = records.map(::splitUnknown)
            val cols = rows.flatMap { it.keys }.distinct()
            ContentQueryResult.Rows(cols, rows)
        }
    }

    private fun normalize(text: String) = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun errorOf(text: String): ContentQueryResult? {
        val marker = text.indexOf("Error while accessing provider")
        val denied = text.contains("SecurityException") || text.contains("Permission Denial")
        if (marker < 0 && !denied) return null
        val detail = text.lineSequence().map { it.trim() }
            .firstOrNull { it.contains("Exception") || it.contains("Permission Denial") }
            ?: text.substring(marker.coerceAtLeast(0)).lineSequence().first().trim()
        return when {
            denied -> ContentQueryResult.PermissionDenied(detail)
            detail.contains("Could not find provider") || detail.contains("Unknown authority") ->
                ContentQueryResult.ProviderMissing(detail)
            else -> ContentQueryResult.Error(detail)
        }
    }

    /** The body of each record (text after `Row: N `), with continuation lines joined by `\n`. */
    private fun records(text: String): List<String> {
        val records = mutableListOf<StringBuilder>()
        for (line in text.split('\n')) {
            val header = rowHeader.find(line)
            if (header != null && header.groupValues[1].toIntOrNull() == records.size) {
                records += StringBuilder(header.groupValues[2])
            } else if (records.isNotEmpty()) {
                records.last().append('\n').append(line)
            }
        }
        return records.map { it.toString().trimEnd('\n') }
    }

    private fun splitKnown(body: String, keys: List<String>): Map<String, String?> {
        val row = LinkedHashMap<String, String?>()
        var cursor = 0
        keys.forEachIndexed { i, key ->
            val prefix = if (i == 0) "$key=" else ", $key="
            val at = body.indexOf(prefix, cursor)
            if (at < 0) {
                row[key] = null
                return@forEachIndexed
            }
            val start = at + prefix.length
            val end = keys.drop(i + 1).firstNotNullOfOrNull { next ->
                body.indexOf(", $next=", start).takeIf { it >= 0 }
            } ?: body.length
            row[key] = cell(body.substring(start, end))
            cursor = end
        }
        return row
    }

    private fun splitUnknown(body: String): Map<String, String?> =
        body.split(fallbackSplit).associate { part ->
            part.substringBefore('=') to cell(part.substringAfter('=', ""))
        }

    private fun cell(raw: String): String? = if (raw == "NULL") null else raw
}
