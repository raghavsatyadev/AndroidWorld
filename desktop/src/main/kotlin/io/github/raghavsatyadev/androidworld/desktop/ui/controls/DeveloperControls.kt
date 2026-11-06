package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.raghavsatyadev.androidworld.desktop.di.DeviceControlsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.display.QuickToggleFieldState
import io.github.raghavsatyadev.androidworld.core.application.display.developer.DeveloperOptionsIntent
import io.github.raghavsatyadev.androidworld.core.application.display.developer.DeveloperToggle
import io.github.raghavsatyadev.androidworld.core.application.display.valueOrNull
import io.github.raghavsatyadev.androidworld.core.domain.display.developer.BackgroundProcessLimit

/** Developer options that are useful while testing an app's lifecycle and rendering. */
@Composable
fun DeveloperScreen(controls: DeviceControlsModule) {
    val eligible by controls.eligible.collectAsState()
    if (!eligible) return NoDevice()
    val state by controls.developer.state.collectAsState()
    val onIntent = controls.developer::handle
    ControlsPage {
        SectionCard("Developer options", trailing = { RefreshButton { onIntent(DeveloperOptionsIntent.Refresh) } }) {
            DEVELOPER_TOGGLES.forEach { (toggle, label, description) ->
                FieldSwitch(
                    label, state.toggles[toggle] ?: QuickToggleFieldState.Loading,
                    { onIntent(DeveloperOptionsIntent.SetToggle(toggle, it)) },
                    description = description,
                )
            }
        }
        SectionCard("Background process limit") {
            Caption("Fewer background processes shows how an app recovers after it is killed.")
            val field = state.processLimit
            ChoiceGroup(
                options = BackgroundProcessLimit.PRESETS,
                selected = field.valueOrNull(),
                label = { if (it < 0) "Standard" else it.toString() },
                onSelect = { onIntent(DeveloperOptionsIntent.SetProcessLimit(it)) },
                enabled = field !is QuickToggleFieldState.Loading && field !is QuickToggleFieldState.Applying,
            )
            field.valueOrNull()?.let { Caption(BackgroundProcessLimit.label(it)) }
            FieldError(field)
        }
    }
}

private val DEVELOPER_TOGGLES = listOf(
    Triple(DeveloperToggle.StayAwake, "Stay awake", "The screen stays on while charging."),
    Triple(DeveloperToggle.DontKeepActivities, "Don't keep activities", "Destroys every activity as soon as you leave it."),
    Triple(DeveloperToggle.ShowViewUpdates, "Show view updates", "Flashes views when they redraw."),
    Triple(DeveloperToggle.ShowSurfaceUpdates, "Show surface updates", "Flashes whole windows when they update."),
)
