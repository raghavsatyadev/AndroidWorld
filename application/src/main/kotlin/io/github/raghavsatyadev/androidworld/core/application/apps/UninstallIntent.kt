package io.github.raghavsatyadev.androidworld.core.application.apps

sealed interface UninstallIntent {
    data object Uninstall : UninstallIntent
}
