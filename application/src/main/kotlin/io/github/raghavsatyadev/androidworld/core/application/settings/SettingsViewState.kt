package io.github.raghavsatyadev.androidworld.core.application.settings

import io.github.raghavsatyadev.androidworld.core.domain.settings.SettingsFieldError
import io.github.raghavsatyadev.androidworld.core.domain.settings.SettingsState

data class SettingsViewState(
    val persisted: SettingsState = SettingsState.DEFAULT,
    val draft: SettingsState = SettingsState.DEFAULT,
    val validationErrors: Set<SettingsFieldError> = emptySet(),
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val errorMessage: String? = null,
) {
    val isModified: Boolean get() = draft != persisted
}
