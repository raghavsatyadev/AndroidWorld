package io.github.raghavsatyadev.androidworld.core.domain.mirroring

/**
 * Persists the user-approved [MirroringOptions] across IDE restarts (task 040, ADR 0006's project
 * scope, matching [io.github.raghavsatyadev.androidworld.core.domain.settings.SettingsRepository]'s contract). The concrete
 * adapter must never let missing, corrupt, or older/newer-schema persisted data throw — it degrades
 * to [MirroringOptions.DEFAULT] (or the closest-recoverable migration) instead.
 */
interface MirroringOptionsRepository {
    suspend fun readOptions(): MirroringOptions
    suspend fun writeOptions(options: MirroringOptions)
}
