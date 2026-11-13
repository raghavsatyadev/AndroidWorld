package io.github.raghavsatyadev.androidworld.core.application.sysconfig

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A write the user is asked to confirm: what it does, the exact command line, and any warning. */
data class SysConfigWrite(val title: String, val commandLine: String, val note: String? = null)

/** Asks the user to confirm a [SysConfigWrite]; false (or a cancelled caller) means don't run it. */
typealias SysConfigConfirm = suspend (SysConfigWrite) -> Boolean

/**
 * What happened to the last write. Writes are checked by reading the value back, because `settings`
 * often exits 0 after printing an exception and `setprop` failures differ between Android versions.
 */
sealed interface WriteResult {
    val message: String

    data class Applied(override val message: String) : WriteResult
    data class Rejected(override val message: String) : WriteResult
}

internal fun StateFlow<DeviceCommandContext>.eligibleSerial(): Flow<DeviceSerial?> =
    map { (it as? DeviceCommandContext.Eligible)?.serial }.distinctUntilChanged()

internal fun failureDetail(stdout: String, stderr: String): String =
    (stderr.ifBlank { stdout }).trim().lineSequence().firstOrNull { it.isNotBlank() }?.let { " $it" } ?: ""
