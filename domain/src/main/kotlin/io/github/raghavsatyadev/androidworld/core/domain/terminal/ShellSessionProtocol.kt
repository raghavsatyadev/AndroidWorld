package io.github.raghavsatyadev.androidworld.core.domain.terminal

/**
 * How the terminal talks to a piped `adb shell`. With no tty, `sh` prints no prompt and gives no
 * sign that a command finished, so every line the user sends is followed by an `echo` of a marker
 * with the exit code and the working directory. The console draws its own prompt from that.
 */
class ShellSessionProtocol(private val token: String) {

    init {
        require(token.isNotBlank() && token.none(Char::isWhitespace)) { "The marker token must be one word" }
    }

    private val marker = "__AW_${token}__"
    private val pattern = Regex("""${Regex.escape(marker)} (\d+) (.*)$""")

    /** Sent once at start: merges stderr into stdout, then reports the starting directory. */
    fun opening(): String = "exec 2>&1\n${endMarker()}"

    /** [line] followed by the marker. LF only: a CR would reach `sh` as part of the command. */
    fun command(line: String): String = line.replace("\r", "") + "\n" + endMarker()

    private fun endMarker(): String = "echo \"$marker \$? \$PWD\"\n"

    /** Splits one output line into text before a marker (if any) and the marker's result. */
    fun parse(line: String): ParsedLine {
        val clean = line.replace("\r", "")
        val match = pattern.find(clean) ?: return ParsedLine(clean, null)
        val before = clean.substring(0, match.range.first)
        val end = CommandEnd(match.groupValues[1].toInt(), match.groupValues[2].ifBlank { "/" })
        return ParsedLine(before.takeIf { it.isNotEmpty() }, end)
    }
}

/** One output line: [text] to show (null when the line held only a marker) and the marker's [end]. */
data class ParsedLine(val text: String?, val end: CommandEnd?)

/** A command finished with [exitCode]; the shell is now in [directory]. */
data class CommandEnd(val exitCode: Int, val directory: String)
