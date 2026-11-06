package io.github.raghavsatyadev.androidworld.core.domain.apps

/**
 * What a task-024 `pm clear` call produced. Mirrors [io.github.raghavsatyadev.androidworld.core.domain.apps.AppLifecycleResult]'s
 * shape: every failure path (non-zero/unknown exit status, a truthful "Failed" body on an
 * otherwise-zero exit, timeout, cancellation, transport/disconnect failure, unsupported transport
 * call) is preserved as a caller-readable [Failure.reason] rather than collapsed into a boolean.
 * [RejectedDuplicate] is distinct from [Failure] — it means the command never ran at all because
 * another clear-data call for the exact same package+serial
 * (see [io.github.raghavsatyadev.androidworld.core.application.apps.ClearDataUseCase]) was already in flight.
 */
sealed interface ClearDataResult {
    data object Success : ClearDataResult
    data class Failure(val reason: String) : ClearDataResult
    data object RejectedDuplicate : ClearDataResult
}
