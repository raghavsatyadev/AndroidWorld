package io.github.raghavsatyadev.androidworld.desktop.ui.emulators

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.Caption
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.core.application.emulator.AvdBusy
import io.github.raghavsatyadev.androidworld.core.application.emulator.EmulatorsIntent
import io.github.raghavsatyadev.androidworld.core.domain.emulator.Avd
import io.github.raghavsatyadev.androidworld.core.domain.emulator.FormFactor

/** What a row's action area shows; animated between as the AVD changes state. */
private sealed interface RowActions {
    data class Busy(val busy: AvdBusy) : RowActions
    data class Running(val inUse: Boolean) : RowActions
    data object Stopped : RowActions
}

/** One AVD: its shape, name and details, and the actions that fit its current state. */
@Composable
internal fun AvdRow(avd: Avd, busy: AvdBusy?, inUse: Boolean, onIntent: (EmulatorsIntent) -> Unit, onUse: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val badge by animateColorAsState(
        if (avd.running) colors.primaryContainer else colors.surfaceContainerHighest,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val badgeContent by animateColorAsState(
        if (avd.running) colors.onPrimaryContainer else colors.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Surface(color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                Icon(formFactorIcon(avd.formFactor), avd.formFactor?.label, tint = badgeContent)
            }
            Column(Modifier.weight(1f)) {
                Text(avd.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Caption(details(avd, inUse))
            }
            val actions = when {
                busy != null -> RowActions.Busy(busy)
                avd.running -> RowActions.Running(inUse)
                else -> RowActions.Stopped
            }
            AnimatedContent(actions, transitionSpec = fadeThrough(), contentAlignment = Alignment.CenterEnd) { shown ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (shown) {
                        is RowActions.Busy -> {
                            LoadingIndicator(Modifier.size(32.dp))
                            Text(shown.busy.label, style = MaterialTheme.typography.labelLarge)
                        }
                        is RowActions.Running -> {
                            if (!shown.inUse) FilledTonalButton(onClick = onUse) { Text("Use") }
                            OutlinedButton(onClick = { onIntent(EmulatorsIntent.Stop(avd)) }) {
                                Icon(Icons.Filled.Stop, null, Modifier.padding(end = 8.dp))
                                Text("Stop")
                            }
                        }
                        RowActions.Stopped -> Button(onClick = { onIntent(EmulatorsIntent.Start(avd)) }) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.padding(end = 8.dp))
                            Text("Start")
                        }
                    }
                }
            }
            MoreMenu(avd, enabled = busy == null, onIntent)
        }
    }
}

@Composable
private fun MoreMenu(avd: Avd, enabled: Boolean, onIntent: (EmulatorsIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) { Icon(Icons.Filled.MoreVert, "More for ${avd.name}") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Cold boot") },
                leadingIcon = { Icon(Icons.Filled.AcUnit, null) },
                enabled = !avd.running,
                onClick = { open = false; onIntent(EmulatorsIntent.Start(avd, cold = true)) },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                enabled = !avd.running,
                onClick = { open = false; onIntent(EmulatorsIntent.Remove(avd)) },
            )
        }
    }
}

private fun details(avd: Avd, inUse: Boolean): String = buildList {
    avd.apiLevel.takeIf { it.isNotBlank() }?.let { add(it.replace("android-", "API ")) }
    avd.device?.let { add(it.replace('_', ' ')) }
    avd.serial?.let { add(if (inUse) "$it · in use" else it) }
}.joinToString(" · ")

internal fun formFactorIcon(formFactor: FormFactor?): ImageVector = when (formFactor) {
    FormFactor.Tablet -> Icons.Filled.Tablet
    FormFactor.Tv -> Icons.Filled.Tv
    FormFactor.Wear -> Icons.Filled.Watch
    FormFactor.Automotive -> Icons.Filled.DirectionsCar
    FormFactor.Desktop -> Icons.Filled.DesktopWindows
    FormFactor.Phone, null -> Icons.Filled.PhoneAndroid
}
