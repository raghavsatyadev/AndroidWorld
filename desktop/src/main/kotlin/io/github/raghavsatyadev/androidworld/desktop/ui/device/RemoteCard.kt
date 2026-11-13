package io.github.raghavsatyadev.androidworld.desktop.ui.device

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.remote.RemoteControlIntent
import io.github.raghavsatyadev.androidworld.core.domain.input.SystemKey
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteCard(graph: AppGraph) {
    val vm = graph.remote.remote
    val state by vm.state.collectAsState()
    fun press(k: SystemKey) = vm.handle(RemoteControlIntent.Press(k))

    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    fun pressAndRefocus(k: SystemKey) {
        press(k)
        focus.requestFocus()
    }

    SectionCard(title = "Remote") {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            remoteKeyFor(e.key)?.let {
                                press(it)
                                true
                            } ?: false
                        }
                    }
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focus.requestFocus()
                    }
                    .border(
                        width = 2.dp,
                        color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left column = D-pad: 3x3 grid
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(Modifier.size(56.dp))
                        FilledTonalIconButton(
                            onClick = { pressAndRefocus(SystemKey.Up) },
                            enabled = state.enabled,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up")
                        }
                        Spacer(Modifier.size(56.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalIconButton(
                            onClick = { pressAndRefocus(SystemKey.Left) },
                            enabled = state.enabled,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Left")
                        }
                        FilledIconButton(
                            onClick = { pressAndRefocus(SystemKey.DpadCenter) },
                            enabled = state.enabled,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Text("OK", style = MaterialTheme.typography.titleSmall)
                        }
                        FilledTonalIconButton(
                            onClick = { pressAndRefocus(SystemKey.Right) },
                            enabled = state.enabled,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Right")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(Modifier.size(56.dp))
                        FilledTonalIconButton(
                            onClick = { pressAndRefocus(SystemKey.Down) },
                            enabled = state.enabled,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down")
                        }
                        Spacer(Modifier.size(56.dp))
                    }
                }

                // Right column with rows of labeled OutlinedButton/FilledTonalButton with icons
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { pressAndRefocus(SystemKey.Back) },
                            enabled = state.enabled,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Back")
                        }
                        OutlinedButton(
                            onClick = { pressAndRefocus(SystemKey.Home) },
                            enabled = state.enabled,
                        ) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Home")
                        }
                        OutlinedButton(
                            onClick = { pressAndRefocus(SystemKey.Menu) },
                            enabled = state.enabled,
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Menu")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { pressAndRefocus(SystemKey.VolumeDown) },
                            enabled = state.enabled,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Vol -")
                        }
                        OutlinedButton(
                            onClick = { pressAndRefocus(SystemKey.Mute) },
                            enabled = state.enabled,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Mute")
                        }
                        OutlinedButton(
                            onClick = { pressAndRefocus(SystemKey.VolumeUp) },
                            enabled = state.enabled,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Vol +")
                        }
                        FilledTonalButton(
                            onClick = { pressAndRefocus(SystemKey.Power) },
                            enabled = state.enabled,
                        ) {
                            Icon(
                                Icons.Filled.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Power")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { pressAndRefocus(SystemKey.MediaPrevious) },
                            enabled = state.enabled,
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                        }
                        IconButton(
                            onClick = { pressAndRefocus(SystemKey.Rewind) },
                            enabled = state.enabled,
                        ) {
                            Icon(Icons.Filled.FastRewind, contentDescription = "Rewind")
                        }
                        IconButton(
                            onClick = { pressAndRefocus(SystemKey.PlayPause) },
                            enabled = state.enabled,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
                        }
                        IconButton(
                            onClick = { pressAndRefocus(SystemKey.FastForward) },
                            enabled = state.enabled,
                        ) {
                            Icon(Icons.Filled.FastForward, contentDescription = "Fast forward")
                        }
                        IconButton(
                            onClick = { pressAndRefocus(SystemKey.MediaNext) },
                            enabled = state.enabled,
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                        }
                        IconButton(
                            onClick = { pressAndRefocus(SystemKey.MediaStop) },
                            enabled = state.enabled,
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop")
                        }
                    }
                }
            }
        }

        Text(
            text =
                "Click the remote, then use the arrow keys, Enter (OK), Esc or Backspace (Back), " +
                    "H (Home), M (Menu), + / - (volume), Space (play/pause).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.enabled) {
            Text(
                text = "Pick a device to use the remote.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun remoteKeyFor(key: Key): SystemKey? =
    when (key) {
        Key.DirectionUp -> SystemKey.Up
        Key.DirectionDown -> SystemKey.Down
        Key.DirectionLeft -> SystemKey.Left
        Key.DirectionRight -> SystemKey.Right
        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> SystemKey.DpadCenter
        Key.Escape, Key.Backspace -> SystemKey.Back
        Key.H -> SystemKey.Home
        Key.M -> SystemKey.Menu
        Key.Plus, Key.Equals, Key.NumPadAdd -> SystemKey.VolumeUp
        Key.Minus, Key.NumPadSubtract -> SystemKey.VolumeDown
        Key.Spacebar, Key.MediaPlayPause -> SystemKey.PlayPause
        else -> null
    }
