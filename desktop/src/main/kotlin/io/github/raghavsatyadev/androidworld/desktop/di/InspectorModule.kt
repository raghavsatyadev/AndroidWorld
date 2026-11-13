package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.application.layout.CaptureLayoutUseCase
import io.github.raghavsatyadev.androidworld.core.application.layout.LayoutInspectorViewModel

class InspectorModule(core: AppGraph) {
    val viewModel = LayoutInspectorViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        capture = CaptureLayoutUseCase(core.adbTransport, core.androidCli),
        currentApp = core.currentAppUseCase,
        selected = core.selectedDevice.state,
        deviceName = core::deviceLabel,
    )
}
