package io.github.raghavsatyadev.androidworld.core.application.wifi

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceListRefresher
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.wifi.MdnsService
import io.github.raghavsatyadev.androidworld.core.domain.wifi.PairingCode
import io.github.raghavsatyadev.androidworld.core.domain.wifi.PairingCodeResult
import io.github.raghavsatyadev.androidworld.core.domain.wifi.QrPairing
import io.github.raghavsatyadev.androidworld.core.domain.wifi.WifiEndpoint
import io.github.raghavsatyadev.androidworld.core.domain.wifi.WifiEndpointResult
import io.github.raghavsatyadev.androidworld.core.domain.wifi.WifiPairingCommands
import io.github.raghavsatyadev.androidworld.core.domain.wifi.WifiPairingStageResult
import io.github.raghavsatyadev.androidworld.core.domain.wifi.findConnect
import io.github.raghavsatyadev.androidworld.core.domain.wifi.findPairing
import io.github.raghavsatyadev.androidworld.core.domain.wifi.parseConnectStageResult
import io.github.raghavsatyadev.androidworld.core.domain.wifi.parseMdnsServices
import io.github.raghavsatyadev.androidworld.core.domain.wifi.parsePairStageResult
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface WifiPairStep {
    data object Idle : WifiPairStep

    data class WaitingForScan(val qrPayload: String, val serviceName: String, val polls: Int) : WifiPairStep

    data class Pairing(val endpoint: String) : WifiPairStep

    data object Connecting : WifiPairStep

    data class Done(val message: String) : WifiPairStep

    data class Failed(val reason: String) : WifiPairStep
}

sealed interface QrPairingIntent {
    data object StartQr : QrPairingIntent

    data class PairWithCode(val pairingEndpoint: String, val code: String, val connectEndpoint: String) : QrPairingIntent

    data object Cancel : QrPairingIntent
}

/**
 * Orchestrates Wi-Fi pairing via QR code or manual pairing code.
 *
 * The QR flow mirrors Android Studio: the desktop shows the QR, the phone advertises
 * `_adb-tls-pairing._tcp` with instance name = the QR's `S:` value, and we discover it with `adb mdns services`.
 */
class QrPairingViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val transport: AdbTransport,
    private val useCase: WifiPairingUseCase,
    private val refresher: DeviceListRefresher,
    private val random: Random = Random.Default,
    private val pollInterval: Duration = 1500.milliseconds,
    private val scanTimeout: Duration = 2.minutes,
    private val connectWait: Duration = 10.seconds,
) {
    private val _state = MutableStateFlow<WifiPairStep>(WifiPairStep.Idle)
    val state: StateFlow<WifiPairStep> = _state.asStateFlow()

    private var activeJob: Job? = null

    fun handle(intent: QrPairingIntent) {
        when (intent) {
            QrPairingIntent.StartQr -> {
                activeJob?.cancel()
                activeJob = scope.launch(dispatchers.io) {
                    startQrFlow()
                }
            }
            is QrPairingIntent.PairWithCode -> {
                activeJob?.cancel()
                activeJob = scope.launch(dispatchers.io) {
                    pairWithCodeFlow(intent)
                }
            }
            QrPairingIntent.Cancel -> {
                activeJob?.cancel()
                _state.value = WifiPairStep.Idle
            }
        }
    }

    private suspend fun startQrFlow() {
        val creds = QrPairing.generate(random)
        _state.value = WifiPairStep.WaitingForScan(
            qrPayload = QrPairing.payload(creds),
            serviceName = creds.serviceName,
            polls = 0,
        )

        var pollCount = 0
        val pairingService: MdnsService? = withTimeoutOrNull(scanTimeout) {
            while (true) {
                val result = transport.executeText(WifiPairingCommands.mdnsServices(5.seconds))
                val services = parseMdnsServices(result.stdout)
                pollCount++
                _state.value = WifiPairStep.WaitingForScan(
                    qrPayload = QrPairing.payload(creds),
                    serviceName = creds.serviceName,
                    polls = pollCount,
                )
                val pairing = services.findPairing(creds.serviceName)
                if (pairing != null) {
                    return@withTimeoutOrNull pairing
                }
                delay(pollInterval)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

        if (pairingService == null) {
            _state.value = WifiPairStep.Failed(
                "No phone scanned the code within 2 minutes. On the phone: Developer options > Wireless debugging > Pair device with QR code."
            )
            return
        }

        val endpoint = pairingService.endpoint
        _state.value = WifiPairStep.Pairing(endpoint.render())
        val code = (PairingCode.parse(creds.password) as PairingCodeResult.Valid).code
        val pairResult = transport.executeText(WifiPairingCommands.pair(endpoint, code, DEFAULT_WIFI_PAIR_TIMEOUT))
        when (val stage = parsePairStageResult(pairResult)) {
            is WifiPairingStageResult.Failure -> {
                _state.value = WifiPairStep.Failed("Pairing failed: ${stage.reason}")
                return
            }
            WifiPairingStageResult.TimedOut -> {
                _state.value = WifiPairStep.Failed("Pairing timed out")
                return
            }
            WifiPairingStageResult.Cancelled -> {
                _state.value = WifiPairStep.Idle
                return
            }
            WifiPairingStageResult.Success -> Unit
        }

        _state.value = WifiPairStep.Connecting
        val connectService: MdnsService? = withTimeoutOrNull(connectWait) {
            while (true) {
                val result = transport.executeText(WifiPairingCommands.mdnsServices(5.seconds))
                val services = parseMdnsServices(result.stdout)
                val found = services.findConnect(endpoint.host.value)
                if (found != null) {
                    return@withTimeoutOrNull found
                }
                delay(pollInterval)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

        if (connectService != null) {
            val connectResult = transport.executeText(
                WifiPairingCommands.connect(connectService.endpoint, DEFAULT_WIFI_CONNECT_TIMEOUT)
            )
            when (val stage = parseConnectStageResult(connectResult)) {
                WifiPairingStageResult.Success -> {
                    _state.value = WifiPairStep.Done("Connected to ${connectService.endpoint.render()}")
                    refresher.refresh()
                }
                is WifiPairingStageResult.Failure -> {
                    _state.value = WifiPairStep.Failed("Paired, but connect failed: ${stage.reason}")
                }
                WifiPairingStageResult.TimedOut -> {
                    _state.value = WifiPairStep.Failed("Paired, but connect failed: Connect timed out")
                }
                WifiPairingStageResult.Cancelled -> {
                    _state.value = WifiPairStep.Idle
                }
            }
        } else {
            _state.value = WifiPairStep.Done("Paired. adb connects to the phone on its own once it shows up.")
            refresher.refresh()
        }
    }

    private suspend fun pairWithCodeFlow(intent: QrPairingIntent.PairWithCode) {
        val pairingEndpointResult = WifiEndpoint.parse(intent.pairingEndpoint)
        val codeResult = PairingCode.parse(intent.code)
        val connectEndpointResult = WifiEndpoint.parse(intent.connectEndpoint)
        val invalidReason = (pairingEndpointResult as? WifiEndpointResult.Invalid)?.reason
            ?: (codeResult as? PairingCodeResult.Invalid)?.reason
            ?: (connectEndpointResult as? WifiEndpointResult.Invalid)?.reason
        if (invalidReason != null) {
            _state.value = WifiPairStep.Failed(invalidReason)
            return
        }

        _state.value = WifiPairStep.Pairing(intent.pairingEndpoint)
        val outcome = useCase.pairAndConnect(
            (pairingEndpointResult as WifiEndpointResult.Valid).endpoint,
            (codeResult as PairingCodeResult.Valid).code,
            (connectEndpointResult as WifiEndpointResult.Valid).endpoint,
        )
        when (outcome) {
            WifiPairingOutcome.Success -> _state.value = WifiPairStep.Done("Paired and connected")
            is WifiPairingOutcome.PairFailed -> _state.value = WifiPairStep.Failed("Pairing failed: ${outcome.reason}")
            is WifiPairingOutcome.ConnectFailed -> _state.value = WifiPairStep.Failed("Connect failed: ${outcome.reason}")
            WifiPairingOutcome.TimedOut -> _state.value = WifiPairStep.Failed("Wi-Fi pairing timed out")
            WifiPairingOutcome.Cancelled -> _state.value = WifiPairStep.Idle
        }
    }

    fun dispose() {
        activeJob?.cancel()
    }
}
