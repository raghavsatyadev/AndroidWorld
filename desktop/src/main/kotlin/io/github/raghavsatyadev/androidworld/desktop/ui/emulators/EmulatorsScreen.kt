package io.github.raghavsatyadev.androidworld.desktop.ui.emulators

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.EmulatorsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.Caption
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ControlsPage
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.emulator.EmulatorMessage
import io.github.raghavsatyadev.androidworld.core.application.emulator.EmulatorsIntent
import io.github.raghavsatyadev.androidworld.core.application.emulator.EmulatorsState
import io.github.raghavsatyadev.androidworld.core.domain.emulator.FormFactor

/** Every AVD on this computer: start, stop, use, create and delete them through the `android` CLI. */
@Composable
fun EmulatorsScreen(module: EmulatorsModule) {
    val vm = module.emulators
    val state by vm.state.collectAsState()
    val selected by module.selectedSerial.collectAsState()
    DisposableEffect(vm) {
        vm.handle(EmulatorsIntent.SetVisible(true))
        onDispose { vm.handle(EmulatorsIntent.SetVisible(false)) }
    }
    if (state.cliMissing) {
        return EmptyState(
            "Google's android CLI isn't installed, so emulators can't be managed here. Install it from developer.android.com/tools/agents.",
            icon = Icons.Filled.ErrorOutline,
        )
    }
    ControlsPage {
        MessageBanner(state.message) { vm.handle(EmulatorsIntent.DismissMessage) }
        SectionCard(
            "Virtual devices",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Without a window", style = MaterialTheme.typography.labelLarge)
                    Switch(state.headless, { vm.handle(EmulatorsIntent.SetHeadless(it)) })
                    RefreshButton { vm.handle(EmulatorsIntent.Refresh) }
                }
            },
        ) {
            AvdList(state, selected, vm::handle, module)
        }
        CreateCard(state, vm::handle)
    }
}

@Composable
private fun AvdList(state: EmulatorsState, selected: String?, onIntent: (EmulatorsIntent) -> Unit, module: EmulatorsModule) {
    when {
        state.loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingIndicator(Modifier.size(32.dp))
            Caption("Reading virtual devices…")
        }
        state.avds.isEmpty() -> Caption("No virtual devices yet. Create one below.")
        else -> Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Caption(
                if (state.headless) "Emulators start without a window; use Screen to see and control one."
                else "Emulators start in their own window.",
            )
            state.avds.forEach { avd ->
                key(avd.id) {
                    AvdRow(
                        avd = avd,
                        busy = state.busy[avd.id],
                        inUse = avd.serial != null && avd.serial == selected,
                        onIntent = onIntent,
                        onUse = { module.use(avd) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCard(state: EmulatorsState, onIntent: (EmulatorsIntent) -> Unit) {
    var profile by rememberSaveable { mutableStateOf<String?>(null) }
    val creating = state.creating
    SectionCard("New virtual device") {
        Caption("Pick a device shape. The first one of a kind may download a system image, which can take a while.")
        if (state.profiles.isEmpty()) {
            Caption("Reading the device shapes the android CLI offers…")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.profiles.forEach { option ->
                    FilterChip(
                        selected = option == (creating ?: profile),
                        onClick = { profile = option },
                        label = { Text(profileLabel(option)) },
                        leadingIcon = { Icon(formFactorIcon(profileFormFactor(option)), null, Modifier.size(18.dp)) },
                        enabled = creating == null,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { profile?.let { onIntent(EmulatorsIntent.Create(it)) } }, enabled = profile != null && creating == null) {
                Icon(Icons.Filled.Add, null, Modifier.padding(end = 8.dp))
                Text("Create")
            }
            AnimatedVisibility(creating != null, enter = reveal(), exit = conceal()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingIndicator(Modifier.size(24.dp))
                    Caption("Creating a ${profileLabel(creating.orEmpty()).lowercase()}…")
                }
            }
        }
    }
}

/** The outcome of the last action, toned by whether it worked. */
@Composable
private fun MessageBanner(message: EmulatorMessage?, onDismiss: () -> Unit) {
    AnimatedVisibility(message != null, enter = reveal(), exit = conceal()) {
        val shown = message ?: return@AnimatedVisibility
        val colors = MaterialTheme.colorScheme
        Surface(
            color = if (shown.isError) colors.errorContainer else colors.secondaryContainer,
            contentColor = if (shown.isError) colors.onErrorContainer else colors.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (shown.isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle, null)
                Text(shown.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Dismiss") }
            }
        }
    }
}

private fun profileLabel(profile: String): String =
    profile.split('_').joinToString(" ").replaceFirstChar { it.uppercase() }

private fun profileFormFactor(profile: String) = when {
    "tablet" in profile -> FormFactor.Tablet
    "desktop" in profile -> FormFactor.Desktop
    else -> FormFactor.Phone
}
