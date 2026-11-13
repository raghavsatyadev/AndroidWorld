package io.github.raghavsatyadev.androidworld.core.domain.content

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue

/**
 * One `content query` call: the provider [uri], the [columns] asked for (always passed as
 * `--projection`, which also tells [ContentQueryParser] where each value ends), and an optional
 * SQL [where] clause and [sort] order. Every runtime part is a quoted [ShellValue].
 */
data class ContentQuerySpec(
    val uri: String,
    val columns: List<String>,
    val where: String? = null,
    val sort: String? = null,
) {
    init {
        require(columns.isNotEmpty()) { "A content query needs at least one column" }
    }

    fun command(): AdbShellCommand {
        val tokens = mutableListOf<ShellToken>(
            ShellToken.Literal("content"), ShellToken.Literal("query"),
            ShellToken.Literal("--uri"), value(uri),
            ShellToken.Literal("--projection"), value(columns.joinToString(":")),
        )
        where?.let { tokens += ShellToken.Literal("--where"); tokens += value(it) }
        sort?.let { tokens += ShellToken.Literal("--sort"); tokens += value(it) }
        return AdbShellCommand(tokens)
    }

    fun parse(stdout: String, stderr: String): ContentQueryResult = ContentQueryParser.parse(stdout, stderr, columns)

    private fun value(text: String) = ShellToken.Value(ShellValue.of(text))
}
