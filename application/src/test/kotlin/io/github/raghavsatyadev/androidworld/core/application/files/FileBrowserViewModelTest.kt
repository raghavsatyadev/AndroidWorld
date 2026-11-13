@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.raghavsatyadev.androidworld.core.application.files

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.FakeAdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.files.FileScope
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test

private val SDCARD = """
    total 8
    drwxrws--- 2 u0_a209  media_rw 4096 2026-11-11 18:36 Download
    -rw-rw---- 1 u0_a209  media_rw   12 2026-11-11 18:36 notes v2.txt
""".trimIndent()

private fun ok(stdout: String = "", stderr: String = "", exit: Int = 0) = AdbTextResult(AdbOutcome.Completed(exit), stdout, stderr)

private fun AdbRequest.line(): String = when (val op = (this as AdbDeviceRequest).operation) {
    is AdbOperation.Shell -> op.command.render()
    is AdbOperation.Host -> op.arguments.joinToString(" ")
    is AdbOperation.Exec -> op.arguments.joinToString(" ")
}

class FileBrowserViewModelTest {
    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val context = MutableStateFlow<DeviceCommandContext>(DeviceCommandContext.Eligible(DeviceSerial.of("emulator-5554")))
    private var answers: (String) -> AdbTextResult = { line ->
        when {
            line.startsWith("ls -la -- '/sdcard/'") -> ok(SDCARD)
            line.startsWith("ls -la -- '/data/'") -> ok(stderr = "ls: /data/: Permission denied", exit = 1)
            line.startsWith("ls -la") -> ok("total 0")
            else -> ok()
        }
    }
    private val transport = FakeAdbTransport(textScript = { answers(it.line()) })
    private var confirmAnswer = true
    private val confirmations = mutableListOf<String>()
    private val vm = FileBrowserViewModel(
        scope = scope,
        dispatchers = object : DispatcherProvider {
            override val default = dispatcher
            override val io = dispatcher
            override val main = dispatcher
        },
        files = DeviceFileService(transport, { _, _, _, _ -> null }, stagingName = { "stage" }),
        commandContext = context,
        defaultPullFolder = { "C:\\Shots" },
        confirm = { title, detail -> confirmations += "$title|$detail"; confirmAnswer },
    )

    private fun settle() = scope.runCurrent()

    private fun lines() = transport.textRequests.map { it.line() }

    @Test
    fun `opens the sdcard with the settings folder as pull target`() {
        settle()
        vm.state.value.path shouldBe "/sdcard"
        vm.state.value.entries.map { it.name } shouldContainExactly listOf("Download", "notes v2.txt")
        vm.state.value.pullFolder shouldBe "C:\\Shots"
    }

    @Test
    fun `a denied folder shows the denied state`() {
        settle()
        vm.handle(FileBrowserIntent.Open("/data"))
        settle()
        vm.state.value.denied shouldBe true
        vm.state.value.path shouldBe "/data"
    }

    @Test
    fun `pull uses adb pull into the chosen folder`() {
        settle()
        vm.handle(FileBrowserIntent.Select("notes v2.txt", additive = false))
        vm.handle(FileBrowserIntent.PullSelected)
        settle()
        lines() shouldContainExactly listOf("ls -la -- '/sdcard/'", "pull /sdcard/notes v2.txt C:\\Shots")
        vm.state.value.message?.text shouldBe "Pulled 1 item to C:\\Shots"
    }

    @Test
    fun `delete asks first and does nothing when refused`() {
        settle()
        confirmAnswer = false
        vm.handle(FileBrowserIntent.Select("Download", additive = false))
        vm.handle(FileBrowserIntent.DeleteSelected)
        settle()
        confirmations.single() shouldContain "/sdcard/Download"
        confirmations.single() shouldContain "emulator-5554"
        lines().none { it.startsWith("rm") } shouldBe true
    }

    @Test
    fun `delete removes the selected paths once confirmed`() {
        settle()
        vm.handle(FileBrowserIntent.Select("Download", additive = false))
        vm.handle(FileBrowserIntent.Select("notes v2.txt", additive = true))
        vm.handle(FileBrowserIntent.DeleteSelected)
        settle()
        lines().filter { it.startsWith("rm") } shouldContainExactly
            listOf("rm -rf -- '/sdcard/Download'", "rm -rf -- '/sdcard/notes v2.txt'")
    }

    @Test
    fun `rename refuses an existing or invalid name`() {
        settle()
        vm.handle(FileBrowserIntent.Rename("notes v2.txt", "Download"))
        vm.state.value.message?.isError shouldBe true
        vm.handle(FileBrowserIntent.Rename("notes v2.txt", "a/b"))
        settle()
        lines().none { it.startsWith("mv") } shouldBe true
        vm.handle(FileBrowserIntent.Rename("notes v2.txt", "notes v3.txt"))
        settle()
        lines() shouldContain "mv -- '/sdcard/notes v2.txt' '/sdcard/notes v3.txt'"
    }

    @Test
    fun `failures show the adb error`() {
        answers = { line -> if (line.startsWith("mkdir")) ok(stderr = "mkdir: 'x': Read-only file system", exit = 1) else ok(SDCARD) }
        settle()
        vm.handle(FileBrowserIntent.NewFolder("x"))
        settle()
        vm.state.value.message?.text shouldBe "mkdir: 'x': Read-only file system"
        vm.state.value.message?.isError shouldBe true
    }

    @Test
    fun `push sends local items into the current folder`() {
        settle()
        vm.handle(FileBrowserIntent.Push(listOf(LocalItem("C:\\in\\a b.png", isDirectory = false), LocalItem("C:\\in\\dir", isDirectory = true))))
        settle()
        lines().filter { it.startsWith("push") } shouldContainExactly
            listOf("push C:\\in\\a b.png /sdcard/", "push C:\\in\\dir /sdcard/")
    }

    @Test
    fun `app scope lists through run-as and stages pushes`() {
        settle()
        vm.handle(FileBrowserIntent.SetScope("com.acme.shop"))
        settle()
        vm.state.value.scope shouldBe FileScope.App("com.acme.shop")
        vm.state.value.path shouldBe "/data/data/com.acme.shop"
        vm.handle(FileBrowserIntent.Push(listOf(LocalItem("C:\\in\\a.txt", isDirectory = false))))
        settle()
        lines() shouldContain "push C:\\in\\a.txt /data/local/tmp/stage"
        lines() shouldContain "run-as 'com.acme.shop' cp -- '/data/local/tmp/stage' '/data/data/com.acme.shop/a.txt'"
        lines() shouldContain "rm -f -- '/data/local/tmp/stage'"
    }

    @Test
    fun `a symlink that is a file gets selected instead of opened`() {
        answers = { line ->
            if (line.contains("/sdcard/f/")) ok(stderr = "ls: /sdcard/f/: Not a directory", exit = 1) else ok(
                "lrwxrwxrwx 1 root root 3 2026-11-13 11:19 f -> x",
            )
        }
        settle()
        vm.handle(FileBrowserIntent.OpenEntry(vm.state.value.entries.single()))
        settle()
        vm.state.value.path shouldBe "/sdcard"
        vm.state.value.selected shouldBe setOf("f")
    }
}
