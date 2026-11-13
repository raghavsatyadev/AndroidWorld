package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.application.remote.RemoteControlViewModel
import io.github.raghavsatyadev.androidworld.core.application.wifi.QrPairingViewModel
import io.github.raghavsatyadev.androidworld.core.application.wifi.WifiPairingUseCase
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

/** TV remote, software keyboard and Wi-Fi pairing (QR + code). */
class RemoteModule(core: AppGraph) {
    val remote =
        RemoteControlViewModel(
            core.childScope(),
            core.dispatchers,
            core.adbTransport,
            core.selectedDevice.state,
        )
    val pairing =
        QrPairingViewModel(
            scope = core.childScope(),
            dispatchers = core.dispatchers,
            transport = core.adbTransport,
            useCase = WifiPairingUseCase(core.adbTransport, core.deviceRepository),
            refresher = core.deviceRepository,
            random = SecureRandom().asKotlinRandom(),
        )
}
