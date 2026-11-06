package io.github.raghavsatyadev.androidworld.core.application.mcp

import io.github.raghavsatyadev.androidworld.core.domain.process.FakeProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessEvent
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private const val URL = "http://127.0.0.1:60996/mcp"
private const val CLAUDE = "/Users/me/.local/bin/claude"

private fun ProcessRequest.argv() = command.arguments.joinToString(" ")

private fun done(code: Int, vararg out: String): List<ProcessEvent> =
    out.map { ProcessEvent.StdoutText(it) } + ProcessEvent.Completed(ProcessOutcome.Completed(code))

class ClaudeCodeConnectorTest {

    @Test
    fun `the claude CLI is found on PATH or in its usual install folders`() {
        val present = setOf("/opt/homebrew/bin/claude", "/Users/me/.local/bin/claude")
        findClaudeCli("/Users/me", "/usr/bin:/opt/homebrew/bin", windows = false, exists = present::contains) shouldBe "/opt/homebrew/bin/claude"
        findClaudeCli("/Users/me", "/usr/bin", windows = false, exists = present::contains) shouldBe "/Users/me/.local/bin/claude"
        findClaudeCli("/Users/me", null, windows = false, exists = { false }) shouldBe null
    }

    @Test
    fun `connecting re-registers the server as androidworld in user scope`() = runTest {
        val processes = FakeProcessExecutor { done(0) }

        ClaudeCodeConnector(processes) { CLAUDE }.connect(URL, "atk_secret") shouldBe ClaudeCodeConnection.Connected

        processes.requests.map { it.command.executable }.toSet() shouldBe setOf(CLAUDE)
        processes.requests.map { it.argv() } shouldBe listOf(
            "mcp remove androidworld -s user",
            "mcp add --transport http --scope user androidworld $URL --header Authorization: Bearer atk_secret",
        )
    }

    @Test
    fun `a missing CLI or a failed add is reported`() = runTest {
        ClaudeCodeConnector(FakeProcessExecutor { done(0) }) { null }.connect(URL, "t") shouldBe ClaudeCodeConnection.CliNotFound

        val failing = FakeProcessExecutor { request ->
            if (request.argv().startsWith("mcp add")) listOf(ProcessEvent.StderrText("boom"), ProcessEvent.Completed(ProcessOutcome.Completed(1))) else done(1)
        }
        ClaudeCodeConnector(failing) { CLAUDE }.connect(URL, "t") shouldBe ClaudeCodeConnection.Failed("boom")
    }
}
