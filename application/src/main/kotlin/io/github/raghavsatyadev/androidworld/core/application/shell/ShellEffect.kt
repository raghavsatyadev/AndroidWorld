package io.github.raghavsatyadev.androidworld.core.application.shell

/** One-shot events [ShellViewModel] emits alongside state — never folded into [ShellViewState]. */
sealed interface ShellEffect {
    data class ShowMessage(val text: String) : ShellEffect
}
