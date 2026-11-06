package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.raghavsatyadev.androidworld.desktop.di.DeviceControlsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.apps.Hint
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.display.QuickToggleFieldState
import io.github.raghavsatyadev.androidworld.core.application.display.QuickTogglesIntent
import io.github.raghavsatyadev.androidworld.core.application.display.QuickTogglesViewState
import io.github.raghavsatyadev.androidworld.core.application.display.toggles.DeviceSettingToggle
import io.github.raghavsatyadev.androidworld.core.application.display.toggles.DeviceSettingTogglesIntent
import io.github.raghavsatyadev.androidworld.core.application.display.toggles.DeviceSettingTogglesViewState
import io.github.raghavsatyadev.androidworld.core.application.display.valueOrNull
import io.github.raghavsatyadev.androidworld.core.domain.display.AnimationsSummary
import io.github.raghavsatyadev.androidworld.core.domain.display.TalkBackProfile
import io.github.raghavsatyadev.androidworld.core.domain.display.toggles.ScreenRotation

/** Display: quick toggles, debug overlays, accessibility, connectivity, rotation, font scale and density. */
@Composable
fun DisplayScreen(controls: DeviceControlsModule) {
    val eligible by controls.eligible.collectAsState()
    if (!eligible) return NoDevice()
    val quick by controls.quickToggles.state.collectAsState()
    val settings by controls.settingToggles.state.collectAsState()
    val font by controls.fontScale.state.collectAsState()
    val density by controls.density.state.collectAsState()
    ControlsPage {
        QuickTogglesCard(quick, controls.quickToggles::handle)
        SettingGroupCard("Debug overlays", RENDERING, settings, controls.settingToggles::handle)
        SettingGroupCard("Accessibility", ACCESSIBILITY, settings, controls.settingToggles::handle)
        SettingGroupCard(
            "Connectivity", CONNECTIVITY, settings, controls.settingToggles::handle,
            caption = "Turning these off can drop a Wi-Fi adb connection.",
        )
        RotationCard(settings.rotation, controls.settingToggles::handle)
        FontScaleCard(font, controls.fontScale::handle)
        DensityCard(density, controls.density::handle)
    }
}

@Composable
private fun QuickTogglesCard(state: QuickTogglesViewState, onIntent: (QuickTogglesIntent) -> Unit) {
    SectionCard("Quick toggles", trailing = { RefreshButton { onIntent(QuickTogglesIntent.Refresh) } }) {
        FieldSwitch("Dark theme", state.darkTheme, { onIntent(QuickTogglesIntent.SetDarkTheme(it)) })
        FieldSwitch(
            "Show taps", state.showTouches, { onIntent(QuickTogglesIntent.SetShowTouches(it)) },
            description = "Draws a dot where the screen is touched.",
        )
        FieldSwitch(
            "Animations off", state.animations.map { it == AnimationsSummary.AllOff },
            { onIntent(QuickTogglesIntent.SetAnimationsOff(it)) },
            description = animationsDescription(state.animations.valueOrNull()),
        )
        FieldSwitch(
            "TalkBack", state.talkBack, { onIntent(QuickTogglesIntent.SetTalkBack(it)) },
            description = talkBackDescription(state.talkBackProfile),
        )
    }
}

@Composable
private fun SettingGroupCard(
    title: String,
    toggles: List<Pair<DeviceSettingToggle, String>>,
    state: DeviceSettingTogglesViewState,
    onIntent: (DeviceSettingTogglesIntent) -> Unit,
    caption: String? = null,
) {
    SectionCard(title) {
        caption?.let { Caption(it) }
        toggles.forEach { (toggle, label) ->
            FieldSwitch(
                label, state.toggles[toggle] ?: QuickToggleFieldState.Loading,
                { onIntent(DeviceSettingTogglesIntent.SetToggle(toggle, it)) },
            )
        }
    }
}

@Composable
private fun RotationCard(field: QuickToggleFieldState<ScreenRotation>, onIntent: (DeviceSettingTogglesIntent) -> Unit) {
    SectionCard("Screen rotation") {
        ChoiceGroup(
            options = ScreenRotation.entries,
            selected = field.valueOrNull(),
            label = { it.name },
            onSelect = { onIntent(DeviceSettingTogglesIntent.SetRotation(it)) },
            enabled = field !is QuickToggleFieldState.Loading && field !is QuickToggleFieldState.Applying,
        )
        FieldError(field)
    }
}

@Composable
internal fun RefreshButton(onClick: () -> Unit) {
    Hint("Read again from the device") {
        IconButton(onClick = onClick) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
    }
}

/** Maps a field's value while keeping its loading/applying/error shape. */
private fun <T, R> QuickToggleFieldState<T>.map(transform: (T) -> R): QuickToggleFieldState<R> = when (this) {
    QuickToggleFieldState.Loading -> QuickToggleFieldState.Loading
    is QuickToggleFieldState.Idle -> QuickToggleFieldState.Idle(transform(value))
    is QuickToggleFieldState.Applying -> QuickToggleFieldState.Applying(transform(optimistic))
    is QuickToggleFieldState.Error -> QuickToggleFieldState.Error(message, lastKnown?.let(transform))
}

private fun animationsDescription(summary: AnimationsSummary?): String = when (summary) {
    is AnimationsSummary.Mixed ->
        "Mixed: window ${summary.window}×, transition ${summary.transition}×, animator ${summary.animatorDuration}×."
    else -> "Sets all three animation scales to 0 for stable UI tests."
}

private fun talkBackDescription(profile: TalkBackProfile?): String = when (profile) {
    TalkBackProfile.Samsung -> "Samsung TalkBack"
    TalkBackProfile.Google -> "Android Accessibility Suite"
    TalkBackProfile.Custom -> "Your commands from Settings"
    null -> "No known TalkBack found on this device."
}

private val RENDERING = listOf(
    DeviceSettingToggle.ShowLayoutBounds to "Show layout bounds",
    DeviceSettingToggle.GpuOverdraw to "Show GPU overdraw",
    DeviceSettingToggle.GpuProfileBars to "Profile GPU rendering bars",
    DeviceSettingToggle.PointerLocation to "Pointer location",
)

private val ACCESSIBILITY = listOf(
    DeviceSettingToggle.BoldText to "Bold text",
    DeviceSettingToggle.InvertColors to "Color inversion",
)

private val CONNECTIVITY = listOf(
    DeviceSettingToggle.AirplaneMode to "Airplane mode",
    DeviceSettingToggle.Wifi to "Wi-Fi",
    DeviceSettingToggle.MobileData to "Mobile data",
)
