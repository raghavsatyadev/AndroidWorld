package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.DeviceControlsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.core.application.locale.LocaleIntent
import io.github.raghavsatyadev.androidworld.core.application.locale.LocaleViewState
import io.github.raghavsatyadev.androidworld.core.application.locale.LocationIntent
import io.github.raghavsatyadev.androidworld.core.application.locale.LocationViewState
import io.github.raghavsatyadev.androidworld.core.domain.location.EmulatorLocationCommand

/** Language & region, and the emulator's GPS location. */
@Composable
fun RegionScreen(controls: DeviceControlsModule) {
    val eligible by controls.eligible.collectAsState()
    if (!eligible) return NoDevice()
    val locale by controls.locale.state.collectAsState()
    val location by controls.location.state.collectAsState()
    ControlsPage {
        LocaleCard(locale, controls.locale::handle)
        LocationCard(location, controls.location::handle)
    }
}

@Composable
private fun LocaleCard(state: LocaleViewState, onIntent: (LocaleIntent) -> Unit) {
    val busy = state.loading || state.applying != null
    SectionCard("Language", trailing = { RefreshButton { onIntent(LocaleIntent.Refresh) } }) {
        val current = state.locale
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when {
                    state.applying != null -> "Switching to ${state.applying}…"
                    current == null -> "Reading…"
                    current.overridden -> "Now ${current.current} (was ${current.original})"
                    else -> "Now ${current.current}"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onIntent(LocaleIntent.Reset) }, enabled = !busy && current?.overridden == true) {
                Text("Reset to ${current?.original ?: "original"}")
            }
        }
        OutlinedTextField(
            state.query, { onIntent(LocaleIntent.Search(it)) },
            label = { Text("Search languages and regions") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Caption(if (state.searching) "Matches" else "Test locales: these catch truncation and right-to-left bugs.")
        AnimatedContent(state.rows, transitionSpec = fadeThrough(), contentKey = { rows -> rows.map { it.tag } }) { rows ->
            Column {
                if (rows.isEmpty()) Caption("No language matches “${state.query}”.")
                rows.forEach { row ->
                    ListItem(
                        headlineContent = { Text(row.tag, fontFamily = FontFamily.Monospace) },
                        supportingContent = { Text(row.explanation) },
                        trailingContent = {
                            if (current?.current.equals(row.tag, ignoreCase = true)) Text("Current", color = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable(enabled = !busy) { onIntent(LocaleIntent.Apply(row.tag)) },
                    )
                }
            }
        }
        ErrorText(state.error)
    }
}

@Composable
private fun LocationCard(state: LocationViewState, onIntent: (LocationIntent) -> Unit) {
    SectionCard("Location") {
        if (!state.emulator) {
            Caption("Setting a GPS fix works on emulators only; a phone uses its own location.")
            return@SectionCard
        }
        Text(
            state.current?.let { (name, point) -> "${name ?: "Custom"}: ${point.latitude}, ${point.longitude}" }
                ?: "No fix set in this session.",
            style = MaterialTheme.typography.titleMedium,
        )
        ChoiceGroup(
            options = EmulatorLocationCommand.cities.map { it.name },
            selected = state.current?.first,
            label = { it },
            onSelect = { onIntent(LocationIntent.Preset(it)) },
            enabled = !state.applying,
        )
        var latitude by rememberSaveable { mutableStateOf("") }
        var longitude by rememberSaveable { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                latitude, { latitude = it }, label = { Text("Latitude") }, singleLine = true,
                isError = state.latitudeError != null, supportingText = state.latitudeError?.let { { Text(it) } },
                modifier = Modifier.width(200.dp),
            )
            OutlinedTextField(
                longitude, { longitude = it }, label = { Text("Longitude") }, singleLine = true,
                isError = state.longitudeError != null, supportingText = state.longitudeError?.let { { Text(it) } },
                modifier = Modifier.width(200.dp),
            )
            FilledTonalButton(
                onClick = { onIntent(LocationIntent.Custom(latitude, longitude)) },
                enabled = !state.applying && latitude.isNotBlank() && longitude.isNotBlank(),
            ) { Text("Set") }
        }
    }
}
