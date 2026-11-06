package io.github.raghavsatyadev.androidworld.desktop.deeplinks

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.DeepLinkAnalysisResult
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.DeepLinkAnalyzer

const val APKANALYZER_MISSING =
    "apkanalyzer not found. Install the Android SDK Command-line Tools (sdkmanager \"cmdline-tools;latest\")."

/**
 * Looks for the real analyzer on every Analyze until one is found, then keeps it, so installing the
 * SDK tools while the app runs works. Without them, Analyze reports what to install instead of failing.
 */
class LazyDeepLinkAnalyzer(private val resolve: () -> DeepLinkAnalyzer?) : DeepLinkAnalyzer {
    @Volatile private var resolved: DeepLinkAnalyzer? = null

    override suspend fun analyze(
        serial: DeviceSerial,
        androidUserId: Int,
        packageName: String,
        versionCode: String?,
        lastUpdateTime: String?,
        codePath: String?,
        refresh: Boolean,
    ): DeepLinkAnalysisResult {
        val analyzer = resolved ?: resolve()?.also { resolved = it } ?: return DeepLinkAnalysisResult.Failure(APKANALYZER_MISSING)
        return analyzer.analyze(serial, androidUserId, packageName, versionCode, lastUpdateTime, codePath, refresh)
    }
}
