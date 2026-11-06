package io.github.raghavsatyadev.androidworld.core.application.apps

import io.github.raghavsatyadev.androidworld.core.domain.devicecontext.ControlPolicy

data class UninstallViewState(
    val controlPolicy: ControlPolicy,
    val selectedPackageName: String?,
    val busy: Boolean,
) {
    val actionEnabled: Boolean
        get() = controlPolicy == ControlPolicy.Enabled && selectedPackageName != null && !busy
}
