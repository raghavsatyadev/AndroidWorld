package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.desktop.tools.AndroidCliPath
import io.github.raghavsatyadev.androidworld.desktop.tools.AndroidSdk
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Location
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Navigator
import io.github.raghavsatyadev.androidworld.core.application.layout.AndroidCli
import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmationQueue
import io.github.raghavsatyadev.androidworld.desktop.mcp.McpController
import io.github.raghavsatyadev.androidworld.desktop.persistence.AppStore
import io.github.raghavsatyadev.androidworld.desktop.persistence.StoreAdapters
import io.github.raghavsatyadev.androidworld.desktop.platform.DesktopClipboard
import io.github.raghavsatyadev.androidworld.desktop.platform.DesktopDispatchers
import io.github.raghavsatyadev.androidworld.desktop.platform.DesktopImageClipboard
import io.github.raghavsatyadev.androidworld.core.adapters.adb.appdata.AdbAppDatabaseTransfer
import io.github.raghavsatyadev.androidworld.core.adapters.adb.binary.BinaryAdbTransport
import io.github.raghavsatyadev.androidworld.core.adapters.adb.device.AdbDeviceRepository
import io.github.raghavsatyadev.androidworld.core.adapters.adb.discovery.DefaultToolLocator
import io.github.raghavsatyadev.androidworld.core.adapters.adb.packages.AppInfoHelper
import io.github.raghavsatyadev.androidworld.core.adapters.adb.packages.DeviceHelperDeployment
import io.github.raghavsatyadev.androidworld.core.adapters.adb.packages.FullShotHelper
import io.github.raghavsatyadev.androidworld.core.adapters.adb.packages.LocaleHelper
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.capture.DesktopRevealInFileManager
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.capture.SettingsBackedCaptureDestination
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.diagnostics.FileDiagnosticsLog
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.DefaultSdkLocationPlatformToolsSource
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.EnvironmentAndroidSdkPlatformToolsSource
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.JvmExecutableFileProbe
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.JvmHostPlatformProvider
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.JvmPathEnvironmentSource
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.SettingsBackedConfiguredToolPathSource
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.mcp.AwtImageScaler
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.mcp.AwtScreenMarker
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.process.JvmProcessExecutor
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.sqlite.JdbcSqliteEngine
import io.github.raghavsatyadev.androidworld.core.application.adb.ConcurrencyLimitedAdbTransport
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDataReader
import io.github.raghavsatyadev.androidworld.core.application.apps.AppLifecycleUseCase
import io.github.raghavsatyadev.androidworld.core.application.apps.ClearDataUseCase
import io.github.raghavsatyadev.androidworld.core.application.apps.UninstallUseCase
import io.github.raghavsatyadev.androidworld.core.application.capture.CaptureScreenshotUseCase
import io.github.raghavsatyadev.androidworld.core.application.capture.CaptureViewModel
import io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppEvent
import io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppUseCase
import io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppViewModel
import io.github.raghavsatyadev.androidworld.core.application.device.SelectedDeviceViewModel
import io.github.raghavsatyadev.androidworld.core.application.devicebar.DeviceBarViewModel
import io.github.raghavsatyadev.androidworld.core.application.devicefacts.DeviceFactsViewModel
import io.github.raghavsatyadev.androidworld.core.application.devicefacts.LoadDeviceFactsUseCase
import io.github.raghavsatyadev.androidworld.core.application.diagnostics.CommandStatsCollector
import io.github.raghavsatyadev.androidworld.core.application.diagnostics.LoggingAdbTransport
import io.github.raghavsatyadev.androidworld.core.application.diagnostics.LoggingProcessExecutor
import io.github.raghavsatyadev.androidworld.core.application.diagnostics.LoggingToolLocator
import io.github.raghavsatyadev.androidworld.core.application.feedback.FeedbackIntent
import io.github.raghavsatyadev.androidworld.core.application.feedback.FeedbackViewModel
import io.github.raghavsatyadev.androidworld.core.application.layout.CaptureLayoutUseCase
import io.github.raghavsatyadev.androidworld.core.application.locale.DeviceLocaleUseCase
import io.github.raghavsatyadev.androidworld.core.application.locale.EmulatorLocationUseCase
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatControlsController
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatPackagePidTracker
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatPackageSelection
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatPidResolver
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatSessionManager
import io.github.raghavsatyadev.androidworld.core.application.mcp.ClaudeCodeConnector
import io.github.raghavsatyadev.androidworld.core.application.mcp.findClaudeCli
import io.github.raghavsatyadev.androidworld.core.application.mcp.tools.McpToolEnvironment
import io.github.raghavsatyadev.androidworld.core.application.mcp.tools.mcpTools
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.capture.TimestampFileNamePolicy
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolId
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolLocator
import io.github.raghavsatyadev.androidworld.core.domain.feedback.FeedbackAction
import io.github.raghavsatyadev.androidworld.core.domain.feedback.FeedbackMessage
import io.github.raghavsatyadev.androidworld.core.domain.feedback.FeedbackSeverity
import io.github.raghavsatyadev.androidworld.core.domain.logcat.LogcatBuffer
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.settings.SettingsState
import io.github.raghavsatyadev.androidworld.core.domain.time.SystemMonotonicClock
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * The app's composition root: the core backend wired for a standalone desktop app (binary adb only,
 * no IDE). Trimmed to the Wave 1 features.
 */
class AppGraph(val store: AppStore = AppStore(), val version: String = "0.1.0") {
    internal val dispatchers = DesktopDispatchers
    private val rootScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    internal fun childScope(): CoroutineScope =
        CoroutineScope(rootScope.coroutineContext + SupervisorJob(parent = rootScope.coroutineContext[Job]))

    val adapters = StoreAdapters(store)

    /** Where the user is; restored from and saved to [AppStore]. */
    val navigator = Navigator(Location.decode(store.state.lastView)) { at -> store.update { it.copy(lastView = at.encode()) } }
    private val diagnosticsLog = FileDiagnosticsLog(AppStore.appDirectory().resolve("logs"))
    private val commandStats = CommandStatsCollector()

    val processExecutor: ProcessExecutor =
        LoggingProcessExecutor(JvmProcessExecutor(), diagnosticsLog, SystemMonotonicClock, commandStats)

    /** Google's `android` CLI, tried before adb for layout trees and screenshots. */
    val androidCli = AndroidCli(processExecutor, executable = { AndroidCliPath.resolved })

    /** The Android SDK (apkanalyzer, apksigner, emulator, …), looked up on each use. */
    val sdk = AndroidSdk()

    private val hostPlatform = JvmHostPlatformProvider()

    val toolLocator: ToolLocator = LoggingToolLocator(DefaultToolLocator(
        configuredPathSource = SettingsBackedConfiguredToolPathSource(adapters.settings),
        androidSdkSources = listOf(
            EnvironmentAndroidSdkPlatformToolsSource(environmentProvider = System::getenv),
            DefaultSdkLocationPlatformToolsSource(hostPlatform = hostPlatform::current),
        ),
        pathEnvironmentSource = JvmPathEnvironmentSource(pathVariableProvider = { System.getenv("PATH") }),
        executableProbe = JvmExecutableFileProbe(),
        hostPlatformProvider = hostPlatform,
        processExecutor = processExecutor,
    ), diagnosticsLog)

    private val binaryTransport: AdbTransport =
        LoggingAdbTransport(BinaryAdbTransport(toolLocator, processExecutor), "binary", diagnosticsLog, SystemMonotonicClock)

    val adbTransport: AdbTransport = ConcurrencyLimitedAdbTransport(binaryTransport)

    private val deviceRepositoryImpl = AdbDeviceRepository(
        scope = childScope(),
        dispatchers = dispatchers,
        binaryTransport = binaryTransport,
        changeSignals = emptyFlow(),
    )
    val deviceRepository get() = deviceRepositoryImpl

    val selectedDevice = SelectedDeviceViewModel(
        scope = childScope(),
        dispatchers = dispatchers,
        deviceRepository = deviceRepositoryImpl,
        persistence = adapters.deviceSelection,
    )

    val deviceBar = DeviceBarViewModel(
        scope = childScope(),
        dispatchers = dispatchers,
        deviceRepository = deviceRepositoryImpl,
        selectedDeviceViewModel = selectedDevice,
        refresher = deviceRepositoryImpl,
    )

    /** The one queue behind every confirmation dialog (UI and MCP). */
    val confirmations = ConfirmationQueue()

    val feedback = FeedbackViewModel(scope = childScope(), dispatchers = dispatchers)

    fun toast(text: String, severity: FeedbackSeverity = FeedbackSeverity.Info, action: FeedbackAction? = null) =
        feedback.handle(FeedbackIntent.Post(FeedbackMessage(id = "aw-${System.nanoTime()}", text = text, severity = severity, action = action)))

    val deviceFacts = DeviceFactsViewModel(
        scope = childScope(),
        dispatchers = dispatchers,
        selectedDeviceState = selectedDevice.state,
        loadDeviceFacts = LoadDeviceFactsUseCase(adbTransport),
        clipboard = DesktopClipboard,
        volatileRefreshInterval = 30.seconds,
    )

    internal val helperDeployment = DeviceHelperDeployment(adbTransport)
    internal val appInfoHelper = AppInfoHelper(adbTransport, deployment = helperDeployment)

    val capture = CaptureViewModel(
        scope = childScope(),
        dispatchers = dispatchers,
        selectedDeviceState = selectedDevice.state,
        captureScreenshotUseCase = CaptureScreenshotUseCase(
            adbTransport = adbTransport,
            captureDestination = SettingsBackedCaptureDestination(adapters.settings),
            fileNamePolicy = TimestampFileNamePolicy(),
            fullShotRenderer = FullShotHelper(adbTransport, helperDeployment),
            clipboard = DesktopImageClipboard,
            copyToClipboard = { store.state.copyScreenshotsToClipboard },
            pngSource = androidCli::screenshot,
        ),
        revealInFileManager = DesktopRevealInFileManager(),
        feedback = feedback,
    )

    internal val lifecycle = AppLifecycleUseCase(adbTransport)
    internal val clearData = ClearDataUseCase(adbTransport)
    internal val uninstall = UninstallUseCase(adbTransport)
    internal val locale = DeviceLocaleUseCase(LocaleHelper(adbTransport, helperDeployment), adapters.originalLocales)
    internal val location = EmulatorLocationUseCase(adbTransport)
    private val currentAppUseCase = CurrentAppUseCase(adbTransport, appInfo = appInfoHelper)

    val currentApp = CurrentAppViewModel(
        scope = childScope(),
        dispatchers = dispatchers,
        currentApp = currentAppUseCase,
        lifecycle = lifecycle,
        clearData = clearData,
        uninstall = uninstall,
        selectedDeviceState = selectedDevice.state,
    ) { event ->
        when (event) {
            is CurrentAppEvent.Done -> toast(event.message, FeedbackSeverity.Success)
            is CurrentAppEvent.Failed -> toast(event.message, FeedbackSeverity.Error)
        }
    }

    val logcatSession = LogcatSessionManager(
        scope = childScope(),
        dispatchers = dispatchers,
        selectedDeviceState = selectedDevice.state,
        transport = adbTransport,
        buffer = LogcatBuffer(capacityBytes = adapters.readSettingsNow().logcatBufferSizeKb * 1024),
    )

    private val logcatPackageSelection = LogcatPackageSelection(scope = childScope(), selectedDeviceState = selectedDevice.state)

    val logcat = LogcatControlsController(
        scope = childScope(),
        dispatchers = dispatchers,
        selectedDeviceState = selectedDevice.state,
        selectedPackageState = logcatPackageSelection.state,
        sessionManager = logcatSession,
        pidTracker = LogcatPackagePidTracker(
            scope = childScope(),
            dispatchers = dispatchers,
            selectedPackageState = logcatPackageSelection.state,
            resolver = LogcatPidResolver(adbTransport),
        ),
        persistence = adapters.logcatControls,
        selectPackage = logcatPackageSelection::select,
    )

    private fun deviceLabel(serial: io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial): String =
        deviceRepositoryImpl.devices.value.firstOrNull { it.serial == serial }?.displayName ?: serial.value

    val mcp = McpController(
        store = store,
        version = version,
        connector = ClaudeCodeConnector(processExecutor) {
            findClaudeCli(
                home = System.getProperty("user.home").orEmpty(),
                path = System.getenv("PATH"),
                windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
                exists = { File(it).isFile },
            )
        },
        deviceLabel = ::deviceLabel,
        confirmations = confirmations,
    ).also { controller ->
        controller.install(
            mcpTools(
                McpToolEnvironment(
                    transport = adbTransport,
                    devices = deviceRepositoryImpl,
                    selected = selectedDevice.state,
                    layout = CaptureLayoutUseCase(adbTransport, androidCli),
                    imageScaler = AwtImageScaler(),
                    marker = AwtScreenMarker(),
                    lifecycle = lifecycle,
                    clearData = clearData,
                    uninstall = uninstall,
                    currentApp = currentAppUseCase,
                    locale = locale,
                    location = location,
                    appData = AppDataReader(adbTransport, AdbAppDatabaseTransfer(adbTransport), JdbcSqliteEngine()),
                    confirmation = controller,
                ),
            ),
        )
    }

    /** Feature areas, built on first use. */
    val apps by lazy { AppsModule(this) }
    val controls by lazy { DeviceControlsModule(this) }
    val emulators by lazy { EmulatorsModule(this) }
    val commands by lazy { CommandsModule(this) }

    /** Saves tool paths / capture dir and drops cached tool lookups that depend on them. */
    fun saveSettings(change: (SettingsState) -> SettingsState) {
        val before = adapters.readSettingsNow()
        val after = change(before)
        if (after == before) return
        rootScope.launch {
            adapters.settings.writeSettings(after)
            if (after.adbPathOverride != before.adbPathOverride) toolLocator.invalidate(ToolId.Adb)
            if (after.scrcpyPathOverride != before.scrcpyPathOverride) toolLocator.invalidate(ToolId.Scrcpy)
            if (after.logcatBufferSizeKb != before.logcatBufferSizeKb) logcatSession.buffer.resize(after.logcatBufferSizeKb * 1024)
            deviceRepositoryImpl.refresh()
        }
    }

    fun dispose() {
        runCatching { mcp.stop() }
        feedback.dispose()
        rootScope.cancel()
    }
}
