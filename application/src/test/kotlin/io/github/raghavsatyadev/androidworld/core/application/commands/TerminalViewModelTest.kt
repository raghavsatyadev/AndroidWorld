@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.raghavsatyadev.androidworld.core.application.commands

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.process.InteractiveProcess
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessCommand
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test

private const val MARK = "__AW_tok__"

private class FakeShell : InteractiveProcess {
    val lines = Channel<String>(Channel.UNLIMITED)
    val written = mutableListOf<String>()
    var closed = false
    override val output: Flow<String> = lines.consumeAsFlow()
    override suspend fun write(text: String) { written += text }
    override fun close() { closed = true; lines.close() }
}

class TerminalViewModelTest {
    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val context = MutableStateFlow<DeviceCommandContext>(DeviceCommandContext.Eligible(DeviceSerial.of("emulator-5554")))
    private val shells = mutableListOf<FakeShell>()
    private val commands = mutableListOf<ProcessCommand>()
    private val vm = TerminalViewModel(
        scope = scope,
        dispatchers = object : DispatcherProvider {
            override val default = dispatcher
            override val io = dispatcher
            override val main = dispatcher
        },
        launcher = { command -> commands += command; FakeShell().also { shells += it } },
        adbPath = { "/sdk/adb" },
        commandContext = context,
        newToken = { "tok" },
    )

    private fun settle() = scope.runCurrent()

    private fun started(): FakeShell {
        settle()
        vm.handle(TerminalIntent.Start)
        settle()
        val shell = shells.last()
        shell.lines.trySend("$MARK 0 /")
        settle()
        return shell
    }

    private fun run(line: String) {
        vm.handle(TerminalIntent.EditInput(line))
        vm.handle(TerminalIntent.Submit)
        settle()
    }

    @Test
    fun `starting opens adb shell on the selected device and becomes ready`() {
        val shell = started()
        commands.single() shouldBe ProcessCommand("/sdk/adb", listOf("-s", "emulator-5554", "shell"))
        shell.written.first() shouldBe "exec 2>&1\necho \"$MARK \$? \$PWD\"\n"
        vm.state.value.status shouldBe TerminalStatus.Ready
        vm.state.value.lines shouldBe emptyList()
    }

    @Test
    fun `a command echoes with its prompt, shows output and takes the new directory and exit code`() {
        val shell = started()
        run("cd /sdcard && ls nope")
        vm.state.value.status shouldBe TerminalStatus.Running
        shell.written.last() shouldBe "cd /sdcard && ls nope\necho \"$MARK \$? \$PWD\"\n"
        shell.lines.trySend("ls: nope: No such file or directory")
        shell.lines.trySend("$MARK 1 /sdcard")
        settle()

        val s = vm.state.value
        s.status shouldBe TerminalStatus.Ready
        s.directory shouldBe "/sdcard"
        s.lastExitCode shouldBe 1
        s.prompt shouldBe "emulator-5554:/sdcard \$"
        s.lines shouldBe listOf(
            TerminalLine.Command("emulator-5554:/ \$", "cd /sdcard && ls nope"),
            TerminalLine.Output("ls: nope: No such file or directory"),
        )
        s.history shouldBe listOf("cd /sdcard && ls nope")
    }

    @Test
    fun `a line typed while a command runs goes to its stdin without a marker`() {
        val shell = started()
        run("read x; echo got \$x")
        run("hello")
        shell.written.last() shouldBe "hello\n"
    }

    @Test
    fun `up and down walk the history`() {
        val shell = started()
        run("one"); shell.lines.trySend("$MARK 0 /"); settle()
        run("two"); shell.lines.trySend("$MARK 0 /"); settle()
        vm.handle(TerminalIntent.HistoryPrevious)
        vm.state.value.input shouldBe "two"
        vm.handle(TerminalIntent.HistoryPrevious)
        vm.state.value.input shouldBe "one"
        vm.handle(TerminalIntent.HistoryNext)
        vm.state.value.input shouldBe "two"
        vm.handle(TerminalIntent.HistoryNext)
        vm.state.value.input shouldBe ""
    }

    @Test
    fun `restart ends the shell and opens a new one`() {
        val first = started()
        run("sleep 300")
        vm.handle(TerminalIntent.Restart)
        settle()
        first.closed shouldBe true
        shells.size shouldBe 2
        vm.state.value.status shouldBe TerminalStatus.Starting
        vm.state.value.lines.last() shouldBe TerminalLine.Notice("Restarted the shell on emulator-5554.")
        vm.state.value.lines.none { it is TerminalLine.Notice && it.text.startsWith("The shell") } shouldBe true
    }

    @Test
    fun `a shell that exits says so and stops taking input`() {
        val shell = started()
        shell.lines.close()
        settle()
        vm.state.value.status shouldBe TerminalStatus.Ended
        vm.state.value.canSubmit shouldBe false
    }

    @Test
    fun `losing the device closes the shell and disables the terminal`() {
        val shell = started()
        context.value = DeviceCommandContext.Disabled.NoDeviceSelected
        settle()
        shell.closed shouldBe true
        vm.state.value.status shouldBe TerminalStatus.NoDevice
        vm.state.value.canSubmit shouldBe false
    }
}
