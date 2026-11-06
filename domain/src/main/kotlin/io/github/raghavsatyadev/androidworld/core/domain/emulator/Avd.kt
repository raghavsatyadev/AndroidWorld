package io.github.raghavsatyadev.androidworld.core.domain.emulator

/** Whether an AVD is running, as `android emulator list --long` reports it. */
enum class AvdStatus { Online, Offline, Other }

/** The kind of device an AVD imitates, for its icon and label. */
enum class FormFactor(val label: String) {
    Phone("Phone"),
    Tablet("Tablet"),
    Tv("TV"),
    Wear("Wear OS"),
    Automotive("Automotive"),
    Desktop("Desktop"),
}

/**
 * One Android Virtual Device. [id] is what the `android` CLI takes; [serial] is set only while it runs.
 * [formFactor] and [device] come from the AVD's `config.ini` and are null when that couldn't be read.
 */
data class Avd(
    val id: String,
    val name: String,
    val apiLevel: String,
    val status: AvdStatus,
    val serial: String? = null,
    val formFactor: FormFactor? = null,
    val device: String? = null,
) {
    val running: Boolean get() = status == AvdStatus.Online
}

/** What an AVD's `config.ini` says about the hardware it imitates. */
data class AvdConfig(val formFactor: FormFactor, val device: String?)
