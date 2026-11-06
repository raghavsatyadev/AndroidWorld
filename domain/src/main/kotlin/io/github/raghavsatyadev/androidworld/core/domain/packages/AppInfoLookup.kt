package io.github.raghavsatyadev.androidworld.core.domain.packages

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial

/** Reads one installed app's label and icon from the device (ADR 0010); `null` when it cannot. */
fun interface AppInfoLookup {
    suspend fun lookup(serial: DeviceSerial, packageName: String): AppInfo?
}
