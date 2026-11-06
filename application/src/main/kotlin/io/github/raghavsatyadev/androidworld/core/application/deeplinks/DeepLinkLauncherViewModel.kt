package io.github.raghavsatyadev.androidworld.core.application.deeplinks

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.DeepLinkCommands
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState
import io.github.raghavsatyadev.androidworld.core.domain.device.toCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.packages.PackageEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The device-wide deep-link launcher: a URI, an optional target package, history and the result of
 * the last launch. [preview] is the `adb` command Open runs, one line or one argument per line.
 */
data class DeepLinkLauncherState(
    val serial: DeviceSerial? = null,
    val uri: String = "",
    val packageName: String = "",
    val coldStart: Boolean = false,
    val commandExpanded: Boolean = false,
    val history: List<RecentDeviceLink> = emptyList(),
    val packages: List<PackageEntry> = emptyList(),
    val launch: DeviceLinkLaunchState = DeviceLinkLaunchState(),
    val handlers: HandlersState = HandlersState.Hidden,
    val preview: String = "",
    val terminalCommand: String = "",
) {
    val uriValid: Boolean get() = DeepLinkCommands.isValidUri(uri.trim())
    val canOpen: Boolean get() = serial != null && uriValid && !launch.busy

    /** A package's label from the device's app list, the package name when it isn't known. */
    fun label(packageName: String): String =
        packages.firstOrNull { it.packageName == packageName }?.label?.takeIf(String::isNotBlank) ?: packageName
}

sealed interface DeepLinkLauncherIntent {
    data class EditUri(val uri: String) : DeepLinkLauncherIntent
    data class EditPackage(val packageName: String) : DeepLinkLauncherIntent
    data class SetColdStart(val enabled: Boolean) : DeepLinkLauncherIntent
    data class SetCommandExpanded(val expanded: Boolean) : DeepLinkLauncherIntent
    data object Open : DeepLinkLauncherIntent
    /** Fills the form from a history entry without launching. */
    data class Reuse(val entry: RecentDeviceLink) : DeepLinkLauncherIntent
    /** Fills the form from a history entry and launches it. */
    data class Rerun(val entry: RecentDeviceLink) : DeepLinkLauncherIntent
    data class Remove(val entry: RecentDeviceLink) : DeepLinkLauncherIntent
    data object DismissResult : DeepLinkLauncherIntent
    /** Re-reads the app list and forgets cached handler answers (apps may have changed). */
    data object Refresh : DeepLinkLauncherIntent
}

/**
 * Builds a [DeviceDeepLinkController] and a [DeepLinkHandlersController] per eligible serial (both are
 * bound to one device) and drops them when the selection changes, so a result or handler answer for
 * one device never shows against another.
 */
class DeepLinkLauncherViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val useCase: DeviceDeepLinkUseCase,
    selectedDeviceState: StateFlow<SelectedDeviceState>,
) {
    private val _state = MutableStateFlow(
        DeepLinkLauncherState(
            packageName = useCase.rememberedPackage(),
            coldStart = useCase.coldStart,
            commandExpanded = useCase.commandExpanded,
            history = useCase.history(),
        ),
    )
    val state: StateFlow<DeepLinkLauncherState> = _state.asStateFlow()

    private class Session(val scope: CoroutineScope, val launcher: DeviceDeepLinkController, val handlers: DeepLinkHandlersController)

    private var session: Session? = null

    init {
        scope.launch(dispatchers.default) {
            selectedDeviceState
                .map { (it.toCommandContext() as? DeviceCommandContext.Eligible)?.serial }
                .distinctUntilChanged()
                .collect(::bind)
        }
    }

    fun handle(intent: DeepLinkLauncherIntent) {
        when (intent) {
            is DeepLinkLauncherIntent.EditUri -> edit { it.copy(uri = intent.uri) }
            is DeepLinkLauncherIntent.EditPackage -> edit { it.copy(packageName = intent.packageName.trim()) }
            is DeepLinkLauncherIntent.SetColdStart -> {
                useCase.coldStart = intent.enabled
                _state.update { it.copy(coldStart = intent.enabled).withPreview() }
            }
            is DeepLinkLauncherIntent.SetCommandExpanded -> {
                useCase.commandExpanded = intent.expanded
                _state.update { it.copy(commandExpanded = intent.expanded).withPreview() }
            }
            DeepLinkLauncherIntent.Open -> open()
            is DeepLinkLauncherIntent.Reuse -> edit { it.copy(uri = intent.entry.uri, packageName = intent.entry.packageName) }
            is DeepLinkLauncherIntent.Rerun -> {
                edit { it.copy(uri = intent.entry.uri, packageName = intent.entry.packageName) }
                open()
            }
            is DeepLinkLauncherIntent.Remove -> {
                useCase.remove(intent.entry)
                _state.update { it.copy(history = useCase.history()) }
            }
            DeepLinkLauncherIntent.DismissResult -> session?.launcher?.dismiss()
            DeepLinkLauncherIntent.Refresh -> {
                session?.handlers?.refresh()
                _state.value.serial?.let(::loadPackages)
                askHandlers()
            }
        }
    }

    /** A form edit: the shown result no longer describes the form, and who handles the link may change. */
    private fun edit(change: (DeepLinkLauncherState) -> DeepLinkLauncherState) {
        _state.update { change(it).withPreview() }
        session?.launcher?.dismiss()
        askHandlers()
    }

    private fun open() {
        val current = _state.value
        val launcher = session?.launcher ?: return
        if (!current.canOpen) return
        useCase.rememberPackage(current.packageName)
        launcher.open(current.uri.trim(), current.packageName, current.coldStart)
    }

    private fun askHandlers() {
        val current = _state.value
        session?.handlers?.request(current.uri, current.packageName)
    }

    private fun bind(serial: DeviceSerial?) {
        session?.scope?.cancel()
        session = null
        _state.update {
            it.copy(serial = serial, launch = DeviceLinkLaunchState(), handlers = HandlersState.Hidden, packages = emptyList()).withPreview()
        }
        if (serial == null) return
        val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        val next = Session(child, DeviceDeepLinkController(child, useCase, serial), DeepLinkHandlersController(child, useCase, serial))
        session = next
        child.launch {
            next.launcher.state.collect { launch ->
                // A finished launch may have added to the history.
                _state.update { it.copy(launch = launch, history = if (launch.busy) it.history else useCase.history()) }
            }
        }
        child.launch { next.handlers.state.collect { handlers -> _state.update { it.copy(handlers = handlers) } } }
        loadPackages(serial)
        askHandlers()
    }

    private fun loadPackages(serial: DeviceSerial) {
        val owner = session?.scope ?: return
        owner.launch(dispatchers.io) {
            val packages = try {
                useCase.packages(serial, emptyList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@launch
            }
            _state.update { if (it.serial == serial) it.copy(packages = packages) else it }
        }
    }

    private fun DeepLinkLauncherState.withPreview(): DeepLinkLauncherState {
        val target = serial ?: return copy(preview = "", terminalCommand = "")
        val link = uri.trim()
        return copy(
            preview = if (commandExpanded) useCase.previewCommand(target, link, packageName, coldStart)
            else useCase.previewLine(target, link, packageName, coldStart),
            // Building the real command validates the URI and package, so only a valid form has one.
            terminalCommand = runCatching { useCase.terminalCommand(target, link, packageName, coldStart) }
                .getOrDefault("").takeIf { DeepLinkCommands.isValidUri(link) }.orEmpty(),
        )
    }
}
