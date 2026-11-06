package io.github.raghavsatyadev.androidworld.core.application.device

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial

/** User/system-triggered inputs [SelectedDeviceViewModel] reduces against [io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState] (ADR 0004). */
sealed interface SelectedDeviceIntent {
    /** Explicit user selection of exactly [serial] — never inferred/implicit (task 009). */
    data class Select(val serial: DeviceSerial) : SelectedDeviceIntent

    /** Explicit user deselection ("no device"), persisted the same as any other selection. */
    data object ClearSelection : SelectedDeviceIntent

    /** Retries resolving the persisted selection after a [io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState.Error]. */
    data object RetryRestore : SelectedDeviceIntent
}
