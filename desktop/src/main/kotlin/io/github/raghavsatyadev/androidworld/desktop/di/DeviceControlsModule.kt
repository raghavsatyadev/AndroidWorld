package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.desktop.platform.DesktopClipboard
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Section
import io.github.raghavsatyadev.androidworld.core.adapters.adb.packages.DevOptionsHelper
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.network.JvmHostNetworkInfo
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeepLinkLauncherIntent
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeepLinkLauncherViewModel
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeviceDeepLinkUseCase
import io.github.raghavsatyadev.androidworld.core.application.display.QuickTogglesIntent
import io.github.raghavsatyadev.androidworld.core.application.display.QuickTogglesViewModel
import io.github.raghavsatyadev.androidworld.core.application.display.density.DensityOverrideTracker
import io.github.raghavsatyadev.androidworld.core.application.display.density.DensityUseCase
import io.github.raghavsatyadev.androidworld.core.application.display.density.DensityViewModel
import io.github.raghavsatyadev.androidworld.core.application.display.developer.DeveloperOptionsIntent
import io.github.raghavsatyadev.androidworld.core.application.display.developer.DeveloperOptionsViewModel
import io.github.raghavsatyadev.androidworld.core.application.display.fontscale.FontScaleViewModel
import io.github.raghavsatyadev.androidworld.core.application.display.toggles.DeviceSettingTogglesIntent
import io.github.raghavsatyadev.androidworld.core.application.display.toggles.DeviceSettingTogglesViewModel
import io.github.raghavsatyadev.androidworld.core.application.locale.LocaleEvent
import io.github.raghavsatyadev.androidworld.core.application.locale.LocaleIntent
import io.github.raghavsatyadev.androidworld.core.application.locale.LocaleViewModel
import io.github.raghavsatyadev.androidworld.core.application.locale.LocationViewModel
import io.github.raghavsatyadev.androidworld.core.application.network.NetworkThrottleIntent
import io.github.raghavsatyadev.androidworld.core.application.network.NetworkThrottleViewModel
import io.github.raghavsatyadev.androidworld.core.application.network.ProxyController
import io.github.raghavsatyadev.androidworld.core.application.network.ProxyIntent
import io.github.raghavsatyadev.androidworld.core.domain.display.TalkBackCustomCommands
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.device.toCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.feedback.FeedbackSeverity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * The Controls area: display toggles, font scale and density, developer options, language and
 * location, proxy and network throttling, and the device deep-link launcher. Every view model is
 * the core's; this only wires them to the desktop app's stores and toasts. Each section re-reads the
 * device when it is opened, because any of these settings can change on the device itself.
 */
class DeviceControlsModule(core: AppGraph) {
    private val scope = core.childScope()
    private val selected = core.selectedDevice.state
    private val commandContext = selected.map { it.toCommandContext() }
        .stateIn(scope, SharingStarted.Eagerly, selected.value.toCommandContext())

    /** Whether the selected device can take commands; every Controls page is empty without one. */
    val eligible = commandContext.map { it is DeviceCommandContext.Eligible }
        .stateIn(scope, SharingStarted.Eagerly, commandContext.value is DeviceCommandContext.Eligible)

    val quickToggles = QuickTogglesViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        transport = core.adbTransport,
        selectedDeviceState = selected,
        customTalkBackCommands = { core.store.state.let { TalkBackCustomCommands(it.talkBackOnCommand, it.talkBackOffCommand) } },
    )

    val settingToggles = DeviceSettingTogglesViewModel(core.childScope(), core.dispatchers, core.adbTransport, selected)

    val fontScale = FontScaleViewModel(core.childScope(), core.dispatchers, core.adbTransport, commandContext)

    val density = DensityViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        useCase = DensityUseCase(core.adbTransport, DensityOverrideTracker()),
        commandContext = commandContext,
    )

    val developer = DeveloperOptionsViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        transport = core.adbTransport,
        activityManager = DevOptionsHelper(core.adbTransport, core.helperDeployment),
        selectedDeviceState = selected,
    )

    val locale = LocaleViewModel(core.childScope(), core.dispatchers, core.locale, selected) { event ->
        when (event) {
            is LocaleEvent.Applied -> core.toast("Language set to ${event.tag}", FeedbackSeverity.Success)
            is LocaleEvent.ResetTo -> core.toast("Language reset to ${event.tag}", FeedbackSeverity.Success)
            is LocaleEvent.Failed -> core.toast(event.message, FeedbackSeverity.Error)
        }
    }

    val location = LocationViewModel(core.childScope(), core.dispatchers, core.location, selected) { message, ok ->
        core.toast(message, if (ok) FeedbackSeverity.Success else FeedbackSeverity.Error)
    }

    val proxy = ProxyController(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        transport = core.adbTransport,
        selectedDeviceState = selected,
        hostNetworkInfo = JvmHostNetworkInfo(),
        recentsPersistence = core.adapters.proxyRecents,
    )

    val throttle = NetworkThrottleViewModel(core.childScope(), core.dispatchers, core.adbTransport, selected)

    val links = DeepLinkLauncherViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        useCase = DeviceDeepLinkUseCase(core.adbTransport, core.adapters.deepLinkMemory, System::currentTimeMillis),
        selectedDeviceState = selected,
    )

    /** Copies a command for the user to run themselves, and says so. */
    val copy: (text: String, message: String) -> Unit = { text, message ->
        DesktopClipboard.writeText(text)
        core.toast(message)
    }

    init {
        core.navigator.location
            .map { it.section }
            .distinctUntilChanged()
            .drop(1)
            .onEach(::refresh)
            .launchIn(scope)
    }

    private fun refresh(section: Section) {
        when (section) {
            Section.Display -> {
                quickToggles.handle(QuickTogglesIntent.Refresh)
                settingToggles.handle(DeviceSettingTogglesIntent.Refresh)
            }
            Section.Developer -> developer.handle(DeveloperOptionsIntent.Refresh)
            Section.Region -> locale.handle(LocaleIntent.Refresh)
            Section.Network -> {
                proxy.handle(ProxyIntent.Refresh)
                throttle.handle(NetworkThrottleIntent.Refresh)
            }
            Section.Links -> links.handle(DeepLinkLauncherIntent.Refresh)
            else -> Unit
        }
    }
}
