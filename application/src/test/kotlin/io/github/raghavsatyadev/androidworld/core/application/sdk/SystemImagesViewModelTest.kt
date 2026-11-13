package io.github.raghavsatyadev.androidworld.core.application.sdk

import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessEvent
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import io.kotest.matchers.collections.shouldBeEmpty
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

private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
    override val default = d
    override val io = d
    override val main = d
}

private class FakePartialDownloads : PartialDownloads {
    var snapshotCount = 0
    val sweeps = mutableListOf<Set<String>>()

    override fun snapshot(): Set<String> {
        snapshotCount++
        return setOf("temp-$snapshotCount")
    }

    override fun sweep(before: Set<String>) {
        sweeps += before
    }
}

private class FakeCliExecutor : ProcessExecutor {
    val calls = mutableListOf<List<String>>()
    var installedPaths = mutableSetOf("system-images/android-34/google_apis/x86_64")
    var availablePaths = mutableSetOf(
        "system-images/android-35/google_apis/x86_64",
        "system-images/android-35/google_apis/arm64-v8a",
    )
    var installGate: CompletableDeferred<Unit>? = null

    override fun execute(request: ProcessRequest): Flow<ProcessEvent> = flow {
        val args = request.command.arguments
        calls += args
        when {
            args.contains("list") -> {
                val lines = mutableListOf<String>()
                lines += "Installed packages:"
                if (installedPaths.isEmpty()) {
                    lines += "  (no installed packages)"
                } else {
                    for (path in installedPaths) {
                        lines += "  $path    1    System Image"
                    }
                }
                lines += "Available packages:"
                for (path in availablePaths) {
                    lines += "  $path    1    System Image"
                }
                for (line in lines) {
                    emit(ProcessEvent.StdoutText(line))
                }
                emit(ProcessEvent.Completed(ProcessOutcome.Completed(0)))
            }
            args.contains("install") -> {
                installGate?.await()
                val path = args.last()
                availablePaths -= path
                installedPaths += path
                emit(ProcessEvent.StdoutText("Unzipping $path..."))
                emit(ProcessEvent.Completed(ProcessOutcome.Completed(0)))
            }
            args.contains("remove") -> {
                val path = args.last()
                installedPaths -= path
                availablePaths += path
                emit(ProcessEvent.Completed(ProcessOutcome.Completed(0)))
            }
            else -> {
                emit(ProcessEvent.Completed(ProcessOutcome.Completed(0)))
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SystemImagesViewModelTest {

    private fun TestScope.subject(
        executor: ProcessExecutor,
        downloads: PartialDownloads,
        hostAbis: Set<String> = setOf("x86_64", "x86"),
        users: ImageUsers = ImageUsers { emptyList() },
        confirm: ImageRemovalConfirmation = ImageRemovalConfirmation { _, _ -> true },
    ): SystemImagesViewModel {
        val cli = SystemImagesCli(executor, { "android" }, { "C:/sdk" }, { "avdmanager" })
        return SystemImagesViewModel(
            scope = backgroundScope,
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            cli = cli,
            downloads = downloads,
            users = users,
            confirmRemoval = confirm,
            hostAbis = hostAbis,
        )
    }

    @Test
    fun `Load fills images and installed and downloadable with ABI filtering`() = runTest {
        val executor = FakeCliExecutor()
        val downloads = FakePartialDownloads()
        val vm = subject(executor, downloads, hostAbis = setOf("x86_64", "x86"))

        vm.handle(SystemImagesIntent.Load)
        runCurrent()

        val state = vm.state.value
        state.loaded shouldBe true
        state.loading shouldBe false
        state.images.size shouldBe 3
        state.installed.map { it.path } shouldContainExactly listOf("system-images/android-34/google_apis/x86_64")
        state.downloadable.map { it.path } shouldContainExactly listOf("system-images/android-35/google_apis/x86_64")

        vm.handle(SystemImagesIntent.SetShowAllAbis(true))
        runCurrent()

        vm.state.value.downloadable.map { it.path } shouldContainExactly listOf(
            "system-images/android-35/google_apis/arm64-v8a",
            "system-images/android-35/google_apis/x86_64",
        )
    }

    @Test
    fun `Install success re-lists and reports non-error message with task cleared`() = runTest {
        val executor = FakeCliExecutor()
        val downloads = FakePartialDownloads()
        val vm = subject(executor, downloads, hostAbis = setOf("x86_64", "x86"))

        vm.handle(SystemImagesIntent.Load)
        runCurrent()

        val toInstall = vm.state.value.downloadable.first { it.path == "system-images/android-35/google_apis/x86_64" }
        vm.handle(SystemImagesIntent.Install(toInstall))
        runCurrent()

        vm.state.value.task shouldBe null
        vm.state.value.message shouldBe ImageMessage("Installed API 35 Google APIs (x86_64).", isError = false)
        vm.state.value.installed.map { it.path } shouldContainExactly listOf(
            "system-images/android-35/google_apis/x86_64",
            "system-images/android-34/google_apis/x86_64",
        )
    }

    @Test
    fun `Cancel during install sweeps partial downloads and reports cancellation message`() = runTest {
        val executor = FakeCliExecutor().apply { installGate = CompletableDeferred() }
        val downloads = FakePartialDownloads()
        val vm = subject(executor, downloads, hostAbis = setOf("x86_64", "x86"))

        vm.handle(SystemImagesIntent.Load)
        runCurrent()

        val toInstall = vm.state.value.downloadable.first()
        vm.handle(SystemImagesIntent.Install(toInstall))
        runCurrent()

        vm.state.value.task shouldBe ImageTask(toInstall, ImageTaskKind.Installing)
        downloads.sweeps.shouldBeEmpty()

        vm.handle(SystemImagesIntent.Cancel)
        runCurrent()

        downloads.sweeps shouldContainExactly listOf(setOf("temp-1"))
        vm.state.value.task shouldBe null
        vm.state.value.message shouldBe ImageMessage("Download cancelled; partial files were removed.", isError = false)
    }

    @Test
    fun `Remove asks confirmation with usedBy and declines without running CLI command`() = runTest {
        val executor = FakeCliExecutor()
        val downloads = FakePartialDownloads()
        var askedUsedBy: List<String>? = null
        val vm = subject(
            executor = executor,
            downloads = downloads,
            users = { listOf("Pixel_8_Pro", "Pixel_Tablet") },
            confirm = { _, usedBy ->
                askedUsedBy = usedBy
                false
            },
        )

        vm.handle(SystemImagesIntent.Load)
        runCurrent()

        val image = vm.state.value.installed.single()
        vm.handle(SystemImagesIntent.Remove(image))
        runCurrent()

        askedUsedBy shouldBe listOf("Pixel_8_Pro", "Pixel_Tablet")
        executor.calls.none { it.contains("remove") } shouldBe true
        vm.state.value.task shouldBe null
    }
}
