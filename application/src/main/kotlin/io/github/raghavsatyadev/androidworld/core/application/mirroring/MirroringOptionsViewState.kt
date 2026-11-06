package io.github.raghavsatyadev.androidworld.core.application.mirroring

import io.github.raghavsatyadev.androidworld.core.domain.mirroring.MirroringOptionFieldError
import io.github.raghavsatyadev.androidworld.core.domain.mirroring.MirroringOptionsDraft

/** The immutable state task 040's options editor renders from and edits, matching
 * [io.github.raghavsatyadev.androidworld.core.application.settings.SettingsViewState]'s shape. */
data class MirroringOptionsViewState(
    val persisted: MirroringOptionsDraft = MirroringOptionsDraft.DEFAULT,
    val draft: MirroringOptionsDraft = MirroringOptionsDraft.DEFAULT,
    val validationErrors: Set<MirroringOptionFieldError> = emptySet(),
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val errorMessage: String? = null,
) {
    val isModified: Boolean get() = draft != persisted
}
