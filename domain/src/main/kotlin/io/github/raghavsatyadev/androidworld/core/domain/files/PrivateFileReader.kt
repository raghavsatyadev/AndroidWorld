package io.github.raghavsatyadev.androidworld.core.domain.files

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial

/**
 * Writes an app-private file's bytes to a local folder (`exec-out run-as … cat`). A port because
 * the core modules have no file system access; `:adapters-adb` implements it.
 */
fun interface PrivateFileReader {
    /** Returns null on success, or why it failed. */
    suspend fun copyToLocal(serial: DeviceSerial, scope: FileScope.App, remote: String, localFolder: String): String?
}
