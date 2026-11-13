package io.github.raghavsatyadev.androidworld.core.adapters.emulator

import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpoint
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DiscoveryFileEndpointLocatorTest {

    @Test
    fun `finds endpoint for matching emulator serial from discovery file`(@TempDir tempDir: File) = runTest {
        val pidFile1 = File(tempDir, "pid_1234.ini")
        pidFile1.writeText(
            """
            port.serial=5554
            avd.name=Pixel_9_Pro
            grpc.port=8554
            grpc.token=tok
            """.trimIndent(),
        )

        val pidFile2 = File(tempDir, "pid_5678.ini")
        pidFile2.writeText(
            """
            port.serial=5558
            avd.name=Tablet_API34
            grpc.port=8558
            grpc.token=tok2
            """.trimIndent(),
        )

        val locator = DiscoveryFileEndpointLocator(directories = { listOf(tempDir) })

        locator.find("emulator-5554") shouldBe EmulatorEndpoint(port = 8554, token = "tok")
        locator.find("emulator-5556") shouldBe null
        locator.find("abc") shouldBe null
    }
}
