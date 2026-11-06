package io.github.raghavsatyadev.androidworld.core.application.wifi

/** User-triggered inputs [WifiPairingViewModel] reduces against [WifiPairingViewState] (ADR 0004). */
sealed interface WifiPairingIntent {
    /** The device bar's "Pair device over Wi-Fi…" entry point (task 011's [io.github.raghavsatyadev.androidworld.core.application.devicebar.DeviceBarIntent.RequestPairOverWifi]). */
    data object Launch : WifiPairingIntent

    /** Cancels the in-flight pair/connect call, if any — a no-op once the flow has already finished. */
    data object Cancel : WifiPairingIntent
}
