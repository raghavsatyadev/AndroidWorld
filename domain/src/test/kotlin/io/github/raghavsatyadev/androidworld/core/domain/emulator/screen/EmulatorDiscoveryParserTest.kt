package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EmulatorDiscoveryParserTest {

    @Test
    fun `full discovery file with CRLF parses port AVD name and gRPC credentials`() {
        val ini = listOf(
            "port.serial=5554",
            "avd.name=Pixel_9_Pro",
            "grpc.port=8554",
            "grpc.token=secret_tok_123",
            "other.field=ignore_me",
        ).joinToString("\r\n")

        val discovery = EmulatorDiscoveryParser.parse(ini)

        discovery shouldBe EmulatorDiscovery(
            consolePort = 5554,
            avdName = "Pixel_9_Pro",
            grpcPort = 8554,
            grpcToken = "secret_tok_123",
        )
        discovery.endpoint() shouldBe EmulatorEndpoint(port = 8554, token = "secret_tok_123")
    }

    @Test
    fun `missing gRPC port yields null endpoint`() {
        val ini = "port.serial=5554\navd.name=Pixel_9\n"
        val discovery = EmulatorDiscoveryParser.parse(ini)

        discovery.grpcPort shouldBe null
        discovery.endpoint() shouldBe null
    }

    @Test
    fun `consolePortOf extracts numeric port from emulator serial`() {
        consolePortOf("emulator-5554") shouldBe 5554
        consolePortOf("emulator-5556") shouldBe 5556
        consolePortOf("emulator-abc") shouldBe null
        consolePortOf("device-5554") shouldBe null
        consolePortOf("") shouldBe null
    }
}
