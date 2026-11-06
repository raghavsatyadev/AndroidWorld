package io.github.raghavsatyadev.androidworld.core.domain.settings

/**
 * Persists [SettingsState] across IDE restarts (ADR 0006: project scope). The concrete adapter (a
 * `PersistentStateComponent`-backed slice of the persisted project state, `:intellij`) must never let
 * missing, corrupt, or older/newer-schema persisted data throw — it degrades to
 * [SettingsState.DEFAULT] (or the closest-recoverable migration) instead, matching
 * [io.github.raghavsatyadev.androidworld.core.domain.device.DeviceSelectionPersistence]'s contract.
 */
interface SettingsRepository {
    suspend fun readSettings(): SettingsState
    suspend fun writeSettings(state: SettingsState)
}
