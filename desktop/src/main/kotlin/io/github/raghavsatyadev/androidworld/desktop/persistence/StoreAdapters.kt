package io.github.raghavsatyadev.androidworld.desktop.persistence

import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeviceDeepLinkMemory
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.RecentDeviceLink
import io.github.raghavsatyadev.androidworld.core.application.locale.OriginalLocaleStore
import io.github.raghavsatyadev.androidworld.core.domain.network.NetworkRecentsPersistence
import io.github.raghavsatyadev.androidworld.core.domain.network.ProxyEndpoint
import io.github.raghavsatyadev.androidworld.core.domain.network.ProxyHost
import io.github.raghavsatyadev.androidworld.core.domain.network.ProxyHostResult
import io.github.raghavsatyadev.androidworld.core.domain.network.ProxyPort
import io.github.raghavsatyadev.androidworld.core.domain.network.ProxyPortResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.apps.PinnedPackagesPersistence
import io.github.raghavsatyadev.androidworld.core.domain.apps.SelectedPackage
import io.github.raghavsatyadev.androidworld.core.domain.apps.SelectedPackagePersistence
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceSelectionPersistence
import io.github.raghavsatyadev.androidworld.core.domain.logcat.LogSeverity
import io.github.raghavsatyadev.androidworld.core.domain.logcat.LogcatControlsPersistence
import io.github.raghavsatyadev.androidworld.core.domain.logcat.LogcatPersistedControls
import io.github.raghavsatyadev.androidworld.core.domain.settings.SettingsRepository
import io.github.raghavsatyadev.androidworld.core.domain.settings.SettingsState

/** The backend's persistence ports, all backed by one [AppStore]. */
class StoreAdapters(private val store: AppStore) {

    val settings = object : SettingsRepository {
        override suspend fun readSettings(): SettingsState = readSettingsNow()

        override suspend fun writeSettings(state: SettingsState) = store.update {
            it.copy(
                adbPathOverride = state.adbPathOverride,
                scrcpyPathOverride = state.scrcpyPathOverride,
                captureDirectory = state.captureDirectory,
                logcatBufferSizeKb = state.logcatBufferSizeKb,
                talkBackOnCommand = state.talkBackOnCommand,
                talkBackOffCommand = state.talkBackOffCommand,
            )
        }
    }

    fun readSettingsNow(): SettingsState = store.state.let {
        SettingsState(
            adbPathOverride = it.adbPathOverride,
            scrcpyPathOverride = it.scrcpyPathOverride,
            captureDirectory = it.captureDirectory,
            logcatBufferSizeKb = it.logcatBufferSizeKb,
            talkBackOnCommand = it.talkBackOnCommand,
            talkBackOffCommand = it.talkBackOffCommand,
        )
    }

    val deviceSelection = object : DeviceSelectionPersistence {
        override suspend fun readSelectedSerial(): DeviceSerial? =
            store.state.selectedSerial?.takeIf(String::isNotBlank)?.let(DeviceSerial::of)

        override suspend fun writeSelectedSerial(serial: DeviceSerial?) =
            store.update { it.copy(selectedSerial = serial?.value) }
    }

    val selectedPackage = object : SelectedPackagePersistence {
        override suspend fun readSelectedPackage(): SelectedPackage? {
            val s = store.state
            val serial = s.selectedPackageSerial?.takeIf(String::isNotBlank) ?: return null
            val name = s.selectedPackageName?.takeIf(String::isNotBlank) ?: return null
            return SelectedPackage(DeviceSerial.of(serial), name)
        }

        override suspend fun writeSelectedPackage(selection: SelectedPackage?) = store.update {
            it.copy(selectedPackageSerial = selection?.serial?.value, selectedPackageName = selection?.packageName)
        }
    }

    val logcatControls = object : LogcatControlsPersistence {
        override suspend fun read(): LogcatPersistedControls = store.state.let { s ->
            LogcatPersistedControls(
                minSeverity = s.logMinLevel?.let { name -> LogSeverity.entries.firstOrNull { it.name == name } },
                packageFilterOn = s.logPackageFilterOn,
                wrap = s.logWrap,
            )
        }

        override suspend fun write(controls: LogcatPersistedControls) = store.update {
            it.copy(logMinLevel = controls.minSeverity?.name, logPackageFilterOn = controls.packageFilterOn, logWrap = controls.wrap)
        }
    }

    val pinnedPackages = object : PinnedPackagesPersistence {
        override suspend fun readPinnedPackages(): Set<String> = store.state.pinnedPackages.toSet()

        override suspend fun writePinnedPackages(packageNames: Set<String>) =
            store.update { it.copy(pinnedPackages = packageNames.sorted()) }
    }

    /** Corrupt or hand-edited entries are dropped, never thrown (the port's contract). */
    val proxyRecents = object : NetworkRecentsPersistence {
        override suspend fun readRecents(): List<ProxyEndpoint> = store.state.recentProxies.mapNotNull(::endpointOrNull)

        override suspend fun writeRecents(recents: List<ProxyEndpoint>) =
            store.update { it.copy(recentProxies = recents.map(ProxyEndpoint::render)) }
    }

    val deepLinkMemory = object : DeviceDeepLinkMemory {
        private fun edit(change: (StoredDeepLinks) -> StoredDeepLinks) = store.update { it.copy(deepLinks = change(it.deepLinks)) }

        override var lastPackage: String
            get() = store.state.deepLinks.lastPackage
            set(value) = edit { it.copy(lastPackage = value) }

        override var coldStart: Boolean
            get() = store.state.deepLinks.coldStart
            set(value) = edit { it.copy(coldStart = value) }

        override var commandExpanded: Boolean
            get() = store.state.deepLinks.commandExpanded
            set(value) = edit { it.copy(commandExpanded = value) }

        override fun entries(): List<RecentDeviceLink> =
            store.state.deepLinks.history.map { RecentDeviceLink(it.uri, it.packageName, it.lastRun, it.handledBy) }

        override fun replace(entries: List<RecentDeviceLink>) = edit { links ->
            links.copy(history = entries.take(MAX_LINK_HISTORY).map { StoredDeepLink(it.uri, it.packageName, it.lastRun, it.handledBy) })
        }
    }

    val originalLocales = object : OriginalLocaleStore {
        override fun get(serial: DeviceSerial): String? = store.state.originalLocales[serial.value]

        override fun put(serial: DeviceSerial, locale: String?) = store.update {
            val next = if (locale == null) it.originalLocales - serial.value else it.originalLocales + (serial.value to locale)
            it.copy(originalLocales = next)
        }
    }

    private companion object {
        const val MAX_LINK_HISTORY = 50
    }
}

/** `host:port`, split on the last colon so a bare IPv6 host survives; `null` when either half is invalid. */
internal fun endpointOrNull(text: String): ProxyEndpoint? {
    val colon = text.lastIndexOf(':')
    if (colon <= 0) return null
    val host = ProxyHost.parse(text.substring(0, colon)) as? ProxyHostResult.Valid ?: return null
    val port = ProxyPort.parse(text.substring(colon + 1)) as? ProxyPortResult.Valid ?: return null
    return ProxyEndpoint(host.host, port.port)
}
