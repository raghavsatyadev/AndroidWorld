package io.github.raghavsatyadev.androidworld.core.domain.terminal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShellSessionProtocolTest {
    private val protocol = ShellSessionProtocol("abc123")

    @Test
    fun `a command is followed by the marker echo and uses LF only`() {
        protocol.command("ls -l\r") shouldBe "ls -l\necho \"__AW_abc123__ \$? \$PWD\"\n"
    }

    @Test
    fun `opening merges stderr and reports the directory`() {
        protocol.opening() shouldBe "exec 2>&1\necho \"__AW_abc123__ \$? \$PWD\"\n"
    }

    @Test
    fun `a marker line gives the exit code and directory`() {
        protocol.parse("__AW_abc123__ 1 /sdcard/Download\r") shouldBe ParsedLine(null, CommandEnd(1, "/sdcard/Download"))
    }

    @Test
    fun `output without a trailing newline keeps the text before the marker`() {
        protocol.parse("hi__AW_abc123__ 0 /") shouldBe ParsedLine("hi", CommandEnd(0, "/"))
    }

    @Test
    fun `an ordinary line has no end`() {
        protocol.parse("total 12") shouldBe ParsedLine("total 12", null)
    }

    @Test
    fun `another session's marker is plain output`() {
        protocol.parse("__AW_zzz__ 0 /") shouldBe ParsedLine("__AW_zzz__ 0 /", null)
    }
}
