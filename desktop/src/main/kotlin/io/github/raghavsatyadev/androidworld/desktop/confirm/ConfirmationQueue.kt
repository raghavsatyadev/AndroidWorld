package io.github.raghavsatyadev.androidworld.desktop.confirm

import io.github.raghavsatyadev.androidworld.core.domain.apps.ClearDataConfirmation
import io.github.raghavsatyadev.androidworld.core.domain.apps.ClearDataConfirmationPort
import io.github.raghavsatyadev.androidworld.core.domain.apps.UninstallConfirmation
import io.github.raghavsatyadev.androidworld.core.domain.apps.UninstallConfirmationPort
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** One question shown in the app's single confirmation dialog. */
data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val destructive: Boolean = true,
    val note: String? = null,
) {
    internal val reply = CompletableDeferred<Boolean>()
}

/**
 * Every yes/no question the app asks, from the UI or from an agent over MCP, goes through here, so
 * only one dialog is ever open and none is lost. Callers wait their turn; a caller that is cancelled
 * (or times out) counts as "no" and its dialog closes.
 */
class ConfirmationQueue : ClearDataConfirmationPort, UninstallConfirmationPort {
    private val turn = Mutex()
    private val _current = MutableStateFlow<ConfirmRequest?>(null)
    val current: StateFlow<ConfirmRequest?> = _current.asStateFlow()

    suspend fun ask(request: ConfirmRequest, timeout: Duration = Duration.INFINITE): Boolean =
        withTimeoutOrNull(timeout) {
            turn.withLock {
                _current.value = request
                try {
                    request.reply.await()
                } finally {
                    _current.compareAndSet(request, null)
                }
            }
        } ?: false

    fun answer(request: ConfirmRequest, confirmed: Boolean) {
        request.reply.complete(confirmed)
    }

    override suspend fun confirmClearData(packageName: String, deviceLabel: String): ClearDataConfirmation =
        if (ask(ConfirmRequest("Clear app data?", "All data of $packageName on $deviceLabel will be deleted.", "Clear data"))) {
            ClearDataConfirmation.Confirmed
        } else ClearDataConfirmation.Cancelled

    override suspend fun confirmUninstall(packageName: String, deviceLabel: String): UninstallConfirmation =
        if (ask(ConfirmRequest("Uninstall app?", "$packageName will be removed from $deviceLabel with its data.", "Uninstall"))) {
            UninstallConfirmation.Confirmed
        } else UninstallConfirmation.Cancelled
}
