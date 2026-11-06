package io.github.raghavsatyadev.androidworld.core.application.devicefacts

/** One-shot events [DeviceFactsViewModel] emits alongside state — never folded into [DeviceFactsViewState]. */
sealed interface DeviceFactsEffect {
    /** The report was written to the clipboard via [io.github.raghavsatyadev.androidworld.core.domain.devicefacts.ClipboardPort]. */
    data object ReportCopied : DeviceFactsEffect
}
