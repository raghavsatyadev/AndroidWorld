package io.github.raghavsatyadev.androidworld.core.application.emulator

import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.emulator.AvdConfig
import io.github.raghavsatyadev.androidworld.core.domain.emulator.AvdStatus
import io.github.raghavsatyadev.androidworld.core.domain.emulator.FormFactor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessEvent
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutputKind
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class Dispatchers(d: CoroutineDispatcher) : DispatcherProvider {
    override val default = d
    override val io = d
    override val main = d
}

/** Scripts the `android` CLI: answers each command by its arguments and records what was run. */
private class FakeCli : ProcessExecutor {
    val calls = mutableListOf<Pair<List<String>, ProcessOutputKind>>()
    var running = setOf("Phone_API37")
    var exit = 0
    var gate: CompletableDeferred<Unit>? = null

    override fun execute(request: ProcessRequest): Flow<ProcessEvent> = flow {
        val args = request.command.arguments
        calls += args to request.outputKind
        when (args.take(2) + args.getOrNull(2).orEmpty()) {
            listOf("emulator", "list", "--long") -> {
                emit(ProcessEvent.StdoutText("AVD ID        AVD Name      API Level    Status    Serial"))
                listOf("Phone_API37", "TV_API34").forEachIndexed { i, id ->
                    val on = id in running
                    emit(ProcessEvent.StdoutText("%-14s%-14s%-13s%-10s%s".format(id, id, "android-3$i", if (on) "Online" else "Offline", if (on) "emulator-555${4 + 2 * i}" else "")))
                }
            }
            listOf("emulator", "create", "--list-profiles") -> emit(ProcessEvent.StdoutText("medium_phone\nsmall_phone"))
            else -> {
                gate?.await()
                if (exit == 0 && args[1] == "start") running = running + args.last()
                if (exit == 0 && args[1] == "stop") running = running - "TV_API34"
                if (exit != 0) emit(ProcessEvent.StderrText("\u001B[31mError: no such device\u001B[0m"))
            }
        }
        emit(ProcessEvent.Completed(ProcessOutcome.Completed(exit)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EmulatorsViewModelTest {

    private fun TestScope.subject(cli: FakeCli, confirm: Boolean = true, exe: String? = "android"): EmulatorsViewModel {
        val configs = AvdConfigSource { id -> if (id.startsWith("TV")) AvdConfig(FormFactor.Tv, "tv_1080p") else null }
        return EmulatorsViewModel(
            backgroundScope,
            Dispatchers(StandardTestDispatcher(testScheduler)),
            EmulatorCli(cli, { exe }, configs),
            { confirm },
        )
    }

    @Test
    fun `becoming visible lists AVDs with their config and loads profiles`() = runTest {
        val vm = subject(FakeCli())
        vm.handle(EmulatorsIntent.SetVisible(true))
        runCurrent()

        val state = vm.state.value
        state.loading shouldBe false
        state.avds.map { it.id to it.status } shouldContainExactly listOf("Phone_API37" to AvdStatus.Online, "TV_API34" to AvdStatus.Offline)
        state.avds[0].serial shouldBe "emulator-5554"
        state.avds[1].formFactor shouldBe FormFactor.Tv
        state.profiles shouldBe listOf("medium_phone", "small_phone")
    }

    @Test
    fun `start is headless by default, detached, and marks the AVD busy until it is up`() = runTest {
        val cli = FakeCli().apply { gate = CompletableDeferred() }
        val vm = subject(cli)
        vm.handle(EmulatorsIntent.SetVisible(true))
        runCurrent()
        val tv = vm.state.value.avds[1]

        vm.handle(EmulatorsIntent.Start(tv))
        vm.handle(EmulatorsIntent.Start(tv))
        runCurrent()
        vm.state.value.busy shouldBe mapOf("TV_API34" to AvdBusy.Starting)

        cli.gate!!.complete(Unit)
        runCurrent()
        val starts = cli.calls.filter { it.first.getOrNull(1) == "start" }
        starts.map { it.first } shouldBe listOf(listOf("emulator", "start", "--headless", "TV_API34"))
        starts.single().second shouldBe ProcessOutputKind.Detached
        vm.state.value.busy shouldBe emptyMap()
        vm.state.value.avds[1].running shouldBe true
        vm.state.value.message shouldBe EmulatorMessage("TV_API34 is running.", isError = false)
    }

    @Test
    fun `a windowed cold start passes no headless flag`() = runTest {
        val cli = FakeCli()
        val vm = subject(cli)
        vm.handle(EmulatorsIntent.SetVisible(true))
        runCurrent()
        vm.handle(EmulatorsIntent.SetHeadless(false))
        vm.handle(EmulatorsIntent.Start(vm.state.value.avds[1], cold = true))
        runCurrent()

        cli.calls.single { it.first.getOrNull(1) == "start" }.first shouldBe listOf("emulator", "start", "--cold", "TV_API34")
    }

    @Test
    fun `stop uses the serial and a CLI failure is shown without colour codes`() = runTest {
        val cli = FakeCli()
        val vm = subject(cli)
        vm.handle(EmulatorsIntent.SetVisible(true))
        runCurrent()
        cli.exit = 1

        vm.handle(EmulatorsIntent.Stop(vm.state.value.avds[0]))
        runCurrent()

        cli.calls.last { it.first.getOrNull(1) == "stop" }.first shouldBe listOf("emulator", "stop", "emulator-5554")
        vm.state.value.message shouldBe EmulatorMessage("Error: no such device", isError = true)
    }

    @Test
    fun `remove asks first and does nothing when declined`() = runTest {
        val cli = FakeCli()
        val vm = subject(cli, confirm = false)
        vm.handle(EmulatorsIntent.SetVisible(true))
        runCurrent()

        vm.handle(EmulatorsIntent.Remove(vm.state.value.avds[1]))
        runCurrent()

        cli.calls.none { it.first.getOrNull(1) == "remove" } shouldBe true
    }

    @Test
    fun `create runs the profile and reports it`() = runTest {
        val cli = FakeCli()
        val vm = subject(cli)
        vm.handle(EmulatorsIntent.Create("medium_phone"))
        runCurrent()

        cli.calls.map { it.first } shouldContainExactly listOf(
            listOf("emulator", "create", "medium_phone"),
            listOf("emulator", "list", "--long"),
        )
        vm.state.value.creating shouldBe null
        vm.state.value.message shouldBe EmulatorMessage("Created a medium phone emulator.", isError = false)
    }

    @Test
    fun `a missing CLI says so instead of listing`() = runTest {
        val vm = subject(FakeCli(), exe = null)
        vm.handle(EmulatorsIntent.SetVisible(true))
        runCurrent()

        vm.state.value.cliMissing shouldBe true
        vm.state.value.loading shouldBe false
    }
}
