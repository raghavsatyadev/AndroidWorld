package io.github.raghavsatyadev.androidworld.core.application.apps

import io.github.raghavsatyadev.androidworld.core.domain.devicecontext.ControlPolicy

/** Immutable presentation state for task 024's Clear-data control. */
data class ClearDataViewState(
    val controlPolicy: ControlPolicy,
    val selectedPackageName: String?,
    val busy: Boolean,
) {
    val actionEnabled: Boolean
        get() = controlPolicy == ControlPolicy.Enabled && selectedPackageName != null && !busy
}
