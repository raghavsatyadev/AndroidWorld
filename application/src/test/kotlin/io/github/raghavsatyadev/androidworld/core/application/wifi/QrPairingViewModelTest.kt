@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.raghavsatyadev.androidworld.core.application.wifi

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbServerRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.FakeAdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceListRefresher
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class QrTestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val default = dispatcher
    override val io = dispatcher
    override val main = dispatcher
}

private class CountingRefresher : DeviceListRefresher {
    var callCount: Int = 0
        private set

    override suspend fun refresh() {
        callCount++
    }
}

class QrPairingViewModelTest {

    private fun harness(
        neverDiscoverMdns: Boolean = false,
        scanTimeout: Duration = 2.minutes,
        pollInterval: Duration = 1500.milliseconds,
        connectWait: Duration = 10.seconds,
    ): Harness {
        val scope = TestScope()
        val dispatchers = QrTestDispatchers(StandardTestDispatcher(scope.testScheduler))
        val refresher = CountingRefresher()

        var mdnsPollCount = 0
        lateinit var viewModel: QrPairingViewModel

        val transport =
            FakeAdbTransport(
                textScript = { request ->
                    val args = (request as AdbServerRequest).arguments
                    when (args.first()) {
                        "mdns" -> {
                            mdnsPollCount++
                            if (mdnsPollCount == 1 || neverDiscoverMdns) {
                                AdbTextResult(
                                    AdbOutcome.Completed(0),
                                    "List of discovered mdns services\n",
                                    "",
                                )
                            } else {
                                val serviceName =
                                    (viewModel.state.value as? WifiPairStep.WaitingForScan)
                                        ?.serviceName ?: "unknown"
                                val stdout =
                                    "List of discovered mdns services\n" +
                                        "$serviceName\t_adb-tls-pairing._tcp\t192.168.1.20:41235\n" +
                                        "adb-X\t_adb-tls-connect._tcp\t192.168.1.20:37099\n"
                                AdbTextResult(AdbOutcome.Completed(0), stdout, "")
                            }
                        }
                        "pair" ->
                            AdbTextResult(
                                AdbOutcome.Completed(0),
                                "Successfully paired to 192.168.1.20:41235 [guid=adb-X]",
                                "",
                            )
                        "connect" ->
                            AdbTextResult(
                                AdbOutcome.Completed(0),
                                "connected to 192.168.1.20:37099",
                                "",
                            )
                        else -> error("Unexpected adb server args: $args")
                    }
                }
            )

        val useCase = WifiPairingUseCase(transport, refresher)
        viewModel =
            QrPairingViewModel(
                scope = scope,
                dispatchers = dispatchers,
                transport = transport,
                useCase = useCase,
                refresher = refresher,
                random = Random(1),
                pollInterval = pollInterval,
                scanTimeout = scanTimeout,
                connectWait = connectWait,
            )

        return Harness(scope, viewModel, transport, refresher)
    }

    private class Harness(
        val scope: TestScope,
        val viewModel: QrPairingViewModel,
        val transport: FakeAdbTransport,
        val refresher: CountingRefresher,
    )

    @Test
    fun `StartQr sets state to WaitingForScan with expected QR payload format`() = runTest {
        val h = harness()
        h.viewModel.handle(QrPairingIntent.StartQr)
        h.scope.runCurrent()

        val state = h.viewModel.state.value.shouldBeInstanceOf<WifiPairStep.WaitingForScan>()
        state.qrPayload.shouldStartWith("WIFI:T:ADB;S:studio-")
        state.qrPayload.shouldEndWith(";;")
    }

    @Test
    fun `full happy path pairs and connects successfully and refreshes device list`() = runTest {
        val h = harness()
        h.viewModel.handle(QrPairingIntent.StartQr)
        h.scope.advanceUntilIdle()

        h.viewModel.state.value shouldBe WifiPairStep.Done("Connected to 192.168.1.20:37099")
        h.refresher.callCount shouldBe 1

        val pairRequest =
            h.transport.textRequests.filterIsInstance<AdbServerRequest>().first {
                it.arguments.first() == "pair"
            }
        pairRequest.arguments.size shouldBe 3
        pairRequest.arguments[0] shouldBe "pair"
        pairRequest.arguments[1] shouldBe "192.168.1.20:41235"
        pairRequest.arguments[2].matches(Regex("^\\d{6}$")) shouldBe true
    }

    @Test
    fun `timeout path when mdns never shows the service fails with scan timeout`() = runTest {
        val h =
            harness(
                neverDiscoverMdns = true,
                scanTimeout = 100.milliseconds,
                pollInterval = 10.milliseconds,
            )
        h.viewModel.handle(QrPairingIntent.StartQr)
        h.scope.advanceUntilIdle()

        val state = h.viewModel.state.value.shouldBeInstanceOf<WifiPairStep.Failed>()
        state.reason shouldBe
            "No phone scanned the code within 2 minutes. On the phone: Developer options > Wireless debugging > Pair device with QR code."
        h.refresher.callCount shouldBe 0
    }

    @Test
    fun `PairWithCode with code 12 fails with 6 digits validation reason`() = runTest {
        val h = harness()
        h.viewModel.handle(
            QrPairingIntent.PairWithCode(
                pairingEndpoint = "192.168.1.20:41235",
                code = "12",
                connectEndpoint = "192.168.1.20:37099",
            )
        )
        h.scope.runCurrent()

        val state = h.viewModel.state.value.shouldBeInstanceOf<WifiPairStep.Failed>()
        state.reason shouldContain "6 digits"
        h.refresher.callCount shouldBe 0
        h.transport.textRequests shouldBe emptyList()
    }

    @Test
    fun `Cancel sets state to Idle`() = runTest {
        val h = harness()
        h.viewModel.handle(QrPairingIntent.StartQr)
        h.scope.runCurrent()

        h.viewModel.state.value.shouldBeInstanceOf<WifiPairStep.WaitingForScan>()

        h.viewModel.handle(QrPairingIntent.Cancel)
        h.scope.runCurrent()

        h.viewModel.state.value shouldBe WifiPairStep.Idle
        h.refresher.callCount shouldBe 0
    }
}
