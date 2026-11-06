package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.desktop.deeplinks.LazyDeepLinkAnalyzer
import io.github.raghavsatyadev.androidworld.desktop.persistence.AppStore
import io.github.raghavsatyadev.androidworld.core.adapters.adb.appdata.AdbAppDatabaseTransfer
import io.github.raghavsatyadev.androidworld.core.adapters.adb.packages.AdbPackageRepository
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.deeplinks.JvmApkDeepLinkAnalyzer
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.deeplinks.JvmAssetLinksFetcher
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.sqlite.JdbcSqliteEngine
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsIntent
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsViewModel
import io.github.raghavsatyadev.androidworld.core.application.apps.AppsIntent
import io.github.raghavsatyadev.androidworld.core.application.apps.AppLifecycleViewModel
import io.github.raghavsatyadev.androidworld.core.application.apps.AppsViewModel
import io.github.raghavsatyadev.androidworld.core.application.apps.ClearDataViewModel
import io.github.raghavsatyadev.androidworld.core.application.apps.SelectedPackageViewModel
import io.github.raghavsatyadev.androidworld.core.application.apps.UninstallViewModel
import io.github.raghavsatyadev.androidworld.core.domain.packages.PackageRepository
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Area
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * The Apps area: package list, the selected app's lifecycle / clear / uninstall actions and its
 * details page. Shares the core's use cases, so the Apps screen, the Device screen's current-app
 * card and MCP all go through the same per-package guards. The same view models
 * as the core's own composition.
 */
class AppsModule(core: AppGraph) {
    val selectedPackage = SelectedPackageViewModel(core.childScope(), core.dispatchers, core.adapters.selectedPackage)

    val packages: PackageRepository = AdbPackageRepository(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        transport = core.adbTransport,
        appInfoHelper = core.appInfoHelper,
    )

    val list = AppsViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        packageRepository = packages,
        selectedDeviceViewModel = core.selectedDevice,
        selectedPackageViewModel = selectedPackage,
        pinnedPackages = core.adapters.pinnedPackages,
    )

    val lifecycle = AppLifecycleViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        selectedDeviceState = core.selectedDevice.state,
        selectedPackageState = selectedPackage.state,
        appLifecycleUseCase = core.lifecycle,
        feedback = core.feedback,
    )

    val clearData = ClearDataViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        selectedDeviceState = core.selectedDevice.state,
        selectedPackageState = selectedPackage.state,
        currentPackageScope = list.currentPackageScope,
        clearDataUseCase = core.clearData,
        confirmationPort = core.confirmations,
        packageRepository = packages,
        feedback = core.feedback,
    )

    val uninstall = UninstallViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        selectedDeviceState = core.selectedDevice.state,
        selectedPackageState = selectedPackage.state,
        currentPackageScope = list.currentPackageScope,
        uninstallUseCase = core.uninstall,
        confirmationPort = core.confirmations,
        packageRepository = packages,
        selectedPackageViewModel = selectedPackage,
        feedback = core.feedback,
    )

    val details = AppDetailsViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        transport = core.adbTransport,
        databases = AdbAppDatabaseTransfer(core.adbTransport),
        sqlite = JdbcSqliteEngine(),
        selectedDeviceState = core.selectedDevice.state,
        deepLinkAnalyzer = LazyDeepLinkAnalyzer {
            val analyzer = core.sdk.tool("cmdline-tools", "apkanalyzer") ?: return@LazyDeepLinkAnalyzer null
            JvmApkDeepLinkAnalyzer(
                transport = core.adbTransport,
                processes = core.processExecutor,
                apkanalyzer = analyzer.toString(),
                apksigner = core.sdk.tool("build-tools", "apksigner")?.toString(),
                cacheRoot = AppStore.appDirectory().resolve("deep-links"),
                projectHash = "androidworld",
                assetLinksFetcher = JvmAssetLinksFetcher(),
            )
        },
    )

    init {
        val scope = core.childScope()
        // The details page follows the selected package, opened once its row (label, icon) is loaded.
        list.state
            .map { state -> state.selectedPackageName to state.rows.firstOrNull { it.packageName == state.selectedPackageName } }
            .filter { (name, row) -> name == null || row != null }
            .distinctUntilChanged { a, b -> a.first == b.first }
            .onEach { (name, row) ->
                if (name == null || row == null) details.handle(AppDetailsIntent.Close)
                else details.handle(AppDetailsIntent.Open(name, row.label, row.icon))
            }
            .launchIn(scope)
        // Coming back to Apps re-reads the package list.
        core.navigator.location
            .map { it.area == Area.Apps }
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .onEach { list.handle(AppsIntent.Refresh) }
            .launchIn(scope)
    }
}
