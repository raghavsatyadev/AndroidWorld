package io.github.raghavsatyadev.androidworld.core.application.mcp

import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessCommand
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessEvent
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import kotlin.time.Duration.Companion.seconds

sealed interface ClaudeCodeConnection {
    data object Connected : ClaudeCodeConnection

    /** No `claude` executable on PATH or in its usual install folders. */
    data object CliNotFound : ClaudeCodeConnection

    data class Failed(val reason: String) : ClaudeCodeConnection
}

/**
 * Registers the MCP server with Claude Code in one click (Settings › AI agents) by running the same
 * `claude mcp add` the setup snippet shows, in user scope under [MCP_SERVER_NAME].
 */
class ClaudeCodeConnector(
    private val processes: ProcessExecutor,
    private val findCli: () -> String?,
) {
    suspend fun connect(url: String, token: String): ClaudeCodeConnection {
        val cli = findCli() ?: return ClaudeCodeConnection.CliNotFound
        // `mcp add` refuses a name that exists; a stale port or token is replaced, not kept.
        run(cli, "mcp", "remove", MCP_SERVER_NAME, "-s", "user")
        val added = run(
            cli, "mcp", "add", "--transport", "http", "--scope", "user", MCP_SERVER_NAME, url,
            "--header", "Authorization: Bearer $token",
        )
        return if (added.exitCode == 0) {
            ClaudeCodeConnection.Connected
        } else {
            ClaudeCodeConnection.Failed(added.output.trim().ifEmpty { added.failure ?: "claude mcp add exited with ${added.exitCode}" })
        }
    }

    private class Result(val exitCode: Int?, val output: String, val failure: String?)

    private suspend fun run(cli: String, vararg arguments: String): Result {
        val output = StringBuilder()
        var exitCode: Int? = null
        var failure: String? = null
        processes.execute(ProcessRequest(ProcessCommand(cli, arguments.toList()), timeout = 30.seconds)).collect { event ->
            when (event) {
                is ProcessEvent.StdoutText -> output.appendLine(event.line)
                is ProcessEvent.StderrText -> output.appendLine(event.line)
                is ProcessEvent.Completed -> when (val outcome = event.outcome) {
                    is ProcessOutcome.Completed -> exitCode = outcome.exitCode
                    ProcessOutcome.TimedOut -> failure = "claude did not answer within 30 s"
                    is ProcessOutcome.StartFailure -> failure = outcome.reason
                }
                else -> Unit
            }
        }
        return Result(exitCode, output.toString(), failure)
    }

}

/**
 * Where `claude` lives: on [path] first (the IDE's PATH often lacks shell additions), then the
 * native installer's `~/.local/bin`, the old `~/.claude/local`, Homebrew and npm global folders.
 */
fun findClaudeCli(home: String, path: String?, windows: Boolean, exists: (String) -> Boolean): String? {
    val names = if (windows) listOf("claude.exe", "claude.cmd") else listOf("claude")
    val separator = if (windows) ';' else ':'
    val dirs = path.orEmpty().split(separator).filter(String::isNotBlank) + listOf(
        "$home/.local/bin",
        "$home/.claude/local",
        "/opt/homebrew/bin",
        "/usr/local/bin",
        "$home/.npm-global/bin",
    )
    return dirs.flatMap { dir -> names.map { "${dir.trimEnd('/', '\\')}/$it" } }.firstOrNull(exists)
}
