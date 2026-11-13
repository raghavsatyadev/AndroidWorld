package io.github.raghavsatyadev.androidworld.core.application.sdk

import io.github.raghavsatyadev.androidworld.core.application.emulator.CliResult
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessEvent
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SystemImage
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class FakeProcessExecutor(
    private val handler: (ProcessRequest) -> List<ProcessEvent>,
) : ProcessExecutor {
    val requests = mutableListOf<ProcessRequest>()

    override fun execute(request: ProcessRequest): Flow<ProcessEvent> = flow {
        requests += request
        for (event in handler(request)) {
            emit(event)
        }
    }
}

class SystemImagesCliTest {

    @Test
    fun `list runs with sdk option and parses installed and available images`() = runTest {
        val listing = """
            Installed packages:
              system-images/android-34/google_apis/x86_64    1    Google APIs Intel x86_64 Atom System Image
            Available packages:
              system-images/android-35/google_apis/x86_64    2    Google APIs Intel x86_64 Atom System Image
              system-images/android-33/google_apis/x86_64    1    Google APIs Intel x86_64 Atom System Image
        """.trimIndent()

        val fake = FakeProcessExecutor {
            listing.lines().map { ProcessEvent.StdoutText(it) } + ProcessEvent.Completed(ProcessOutcome.Completed(0))
        }
        val cli = SystemImagesCli(fake, { "android" }, { "C:/sdk" }, { "avdmanager" })

        val images = cli.list()
        images?.size shouldBe 3
        val req = fake.requests.single()
        req.command.arguments shouldBe listOf("--sdk=C:/sdk", "sdk", "list", "--all")
        req.timeout shouldBe 3.minutes

        images?.map { it.apiLevel to it.installed } shouldBe listOf(
            "35" to false,
            "34" to true,
            "33" to false,
        )
    }

    @Test
    fun `install returns Failed when stdout has failure message and exit is 0`() = runTest {
        val fake = FakeProcessExecutor {
            listOf(
                ProcessEvent.StdoutText("Package system-images/android-99/x/x86 not found."),
                ProcessEvent.Completed(ProcessOutcome.Completed(0)),
            )
        }
        val cli = SystemImagesCli(fake, { "android" }, { "C:/sdk" }, { "avdmanager" })

        val result = cli.install("system-images/android-99/x/x86")
        result shouldBe CliResult.Failed("Package system-images/android-99/x/x86 not found.")
    }

    @Test
    fun `install returns Ok on real output lines and exit 0`() = runTest {
        val fake = FakeProcessExecutor {
            listOf(
                ProcessEvent.StdoutText("\rhttps://dl.google.com/android/repository/sys-img/android/armeabi-v7a-10_r05.zip..."),
                ProcessEvent.StdoutText("\rUnzipping system-images/android-10/default/armeabi-v7a......"),
                ProcessEvent.Completed(ProcessOutcome.Completed(0)),
            )
        }
        val cli = SystemImagesCli(fake, { "android" }, { "C:/sdk" }, { "avdmanager" })

        val result = cli.install("system-images/android-10/default/armeabi-v7a")
        result shouldBe CliResult.Ok
    }

    @Test
    fun `remove returns Failed on exit 1`() = runTest {
        val fake = FakeProcessExecutor {
            listOf(
                ProcessEvent.StdoutText("None of the specified packages were found."),
                ProcessEvent.Completed(ProcessOutcome.Completed(1)),
            )
        }
        val cli = SystemImagesCli(fake, { "android" }, { "C:/sdk" }, { "avdmanager" })

        val result = cli.remove("system-images/android-10/default/armeabi-v7a")
        result shouldBe CliResult.Failed("None of the specified packages were found.")
    }

    @Test
    fun `createAvd passes quoted packageId for bat and unquoted for non-bat with sdk environment`() = runTest {
        val fakeBat = FakeProcessExecutor {
            listOf(ProcessEvent.Completed(ProcessOutcome.Completed(0)))
        }
        val cliBat = SystemImagesCli(
            fakeBat,
            { "android" },
            { "C:/sdk" },
            { "C:/sdk/cmdline-tools/latest/bin/avdmanager.bat" },
        )

        cliBat.createAvd("N", "system-images;android-34;google_apis;x86_64", "medium_phone") shouldBe CliResult.Ok
        val reqBat = fakeBat.requests.single()
        reqBat.command.arguments shouldBe listOf(
            "create", "avd", "-n", "N", "-k", "\"system-images;android-34;google_apis;x86_64\"", "-d", "medium_phone",
        )
        reqBat.command.environment["ANDROID_HOME"] shouldBe "C:/sdk"
        reqBat.command.environment["ANDROID_SDK_ROOT"] shouldBe "C:/sdk"

        val fakeSh = FakeProcessExecutor {
            listOf(ProcessEvent.Completed(ProcessOutcome.Completed(0)))
        }
        val cliSh = SystemImagesCli(
            fakeSh,
            { "android" },
            { "C:/sdk" },
            { "C:/sdk/cmdline-tools/latest/bin/avdmanager" },
        )

        cliSh.createAvd("N", "system-images;android-34;google_apis;x86_64", "medium_phone") shouldBe CliResult.Ok
        val reqSh = fakeSh.requests.single()
        reqSh.command.arguments shouldBe listOf(
            "create", "avd", "-n", "N", "-k", "system-images;android-34;google_apis;x86_64", "-d", "medium_phone",
        )
    }

    @Test
    fun `createAvd returns Failed when avdmanager is null`() = runTest {
        val fake = FakeProcessExecutor { emptyList() }
        val cli = SystemImagesCli(fake, { "android" }, { "C:/sdk" }, { null })

        val result = cli.createAvd("N", "system-images;android-34;google_apis;x86_64", "medium_phone")
        result shouldBe CliResult.Failed("avdmanager wasn't found in the Android SDK (cmdline-tools).")
    }

    @Test
    fun `AvdNames deviceFor maps profiles correctly`() {
        AvdNames.deviceFor("small_phone") shouldBe "small_phone"
        AvdNames.deviceFor("medium_phone") shouldBe "medium_phone"
        AvdNames.deviceFor("medium_tablet") shouldBe "medium_tablet"
        AvdNames.deviceFor("small_desktop") shouldBe "desktop_small"
        AvdNames.deviceFor("medium_desktop") shouldBe "desktop_medium"
        AvdNames.deviceFor("large_desktop") shouldBe "desktop_large"
        AvdNames.deviceFor("foldable") shouldBe "7.6in Foldable"
        AvdNames.deviceFor("my_foldable_device") shouldBe "7.6in Foldable"
        AvdNames.deviceFor("other_profile") shouldBe "medium_phone"
    }

    @Test
    fun `AvdNames unique builds sanitized name and handles collisions`() {
        val image = SystemImage(
            path = "system-images/android-37.2/google_apis/x86_64",
            apiLevel = "37.2",
            extension = null,
            tag = "google_apis",
            abi = "x86_64",
            version = "1",
            description = "Google APIs",
            installed = true,
        )

        AvdNames.unique("medium_phone", image, emptyList()) shouldBe "Medium_Phone_API_37.2"
        AvdNames.unique("medium_phone", image, listOf("Medium_Phone_API_37.2")) shouldBe "Medium_Phone_API_37.2_2"
        AvdNames.unique(
            "medium_phone",
            image,
            listOf("medium_phone_api_37.2", "Medium_Phone_API_37.2_2"),
        ) shouldBe "Medium_Phone_API_37.2_3"

        val extImage = image.copy(extension = 10)
        AvdNames.unique("medium_phone", extImage, emptyList()) shouldBe "Medium_Phone_API_37.2_ext10"
    }
}
