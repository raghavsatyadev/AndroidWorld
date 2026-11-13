package io.github.raghavsatyadev.androidworld.core.domain.content

/** RFC 4180 CSV: fields holding a comma, quote or line break are quoted, quotes doubled, CRLF rows. */
object CsvFormat {
    fun format(table: PimTableData): String = buildString {
        appendRow(table.headers)
        table.rows.forEach { appendRow(it) }
    }

    fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, ",") { field(it) }
        append("\r\n")
    }
}
