package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.adapters.emulator.DiscoveryFileEndpointLocator
import io.github.raghavsatyadev.androidworld.core.adapters.emulator.GrpcEmulatorConnector
import io.github.raghavsatyadev.androidworld.core.application.emulator.screen.AdbEmulatorKeyFallback
import io.github.raghavsatyadev.androidworld.core.application.emulator.screen.EmulatorScreenViewModel
import io.github.raghavsatyadev.androidworld.desktop.emulator.SdkSkins

/**
 * Feature module for the embedded emulator screen: manages gRPC streaming and input injection
 * to running Android emulators, with skin loading from the Android SDK.
 */
class EmulatorScreenModule(core: AppGraph) {
    val viewModel = EmulatorScreenViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        locator = DiscoveryFileEndpointLocator(),
        connector = GrpcEmulatorConnector(),
        keyFallback = AdbEmulatorKeyFallback(core.adbTransport),
    )
    val skins = SdkSkins()
}
