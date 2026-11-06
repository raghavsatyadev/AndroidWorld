package io.github.raghavsatyadev.androidworld.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.devicebar.DeviceBarIntent
import io.github.raghavsatyadev.androidworld.core.application.devicebar.DeviceBarPresentation
import io.github.raghavsatyadev.androidworld.core.application.devicebar.DeviceBarViewModel
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceConnectionKind
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceConnectionState

private fun kindIcon(kind: DeviceConnectionKind): ImageVector = when (kind) {
    DeviceConnectionKind.Usb -> Icons.Filled.Usb
    DeviceConnectionKind.Wifi -> Icons.Filled.Wifi
    DeviceConnectionKind.Emulator -> Icons.Filled.Computer
}

/** The selected device as a tonal pill; opens the device picker as a dropdown. */
@Composable
fun DeviceBar(viewModel: DeviceBarViewModel) {
    val state by viewModel.state.collectAsState()
    val (label, icon) = when (val bar = state.bar) {
        DeviceBarPresentation.Loading -> "Looking for devices…" to Icons.Filled.PhoneAndroid
        DeviceBarPresentation.NoDevice -> "No device connected" to Icons.Filled.PhoneAndroid
        is DeviceBarPresentation.SelectDevice -> "Select a device (${bar.deviceCount})" to Icons.Filled.PhoneAndroid
        is DeviceBarPresentation.Online -> bar.device.displayName to kindIcon(bar.device.connectionKind)
        is DeviceBarPresentation.Unauthorized -> "${bar.device.displayName} · allow USB debugging" to kindIcon(bar.device.connectionKind)
        is DeviceBarPresentation.Offline -> "${bar.device.displayName} · offline" to kindIcon(bar.device.connectionKind)
        is DeviceBarPresentation.Error -> "adb: ${bar.message}" to Icons.Filled.PhoneAndroid
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilledTonalButton(onClick = { viewModel.handle(DeviceBarIntent.OpenPicker) }) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, maxLines = 1)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(
                expanded = state.picker.isOpen,
                onDismissRequest = { viewModel.handle(DeviceBarIntent.ClosePicker) },
                shape = MaterialTheme.shapes.large,
            ) {
                if (state.picker.items.isEmpty()) {
                    DropdownMenuItem(text = { Text("No devices. Connect one with USB debugging on.") }, onClick = {}, enabled = false)
                }
                state.picker.items.forEach { item ->
                    DropdownMenuItem(
                        leadingIcon = { Icon(kindIcon(item.connectionKind), null) },
                        trailingIcon = { if (item.isSelected) Icon(Icons.Filled.Check, null) },
                        text = {
                            Column {
                                Text(item.model?.replace('_', ' ') ?: item.serial.value)
                                Text(
                                    "${item.serial.value} · ${stateLabel(item.connectionState)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { viewModel.handle(DeviceBarIntent.SelectDevice(item.serial)) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Wifi, null) },
                    text = { Text("Pair device over Wi-Fi…") },
                    onClick = {
                        viewModel.handle(DeviceBarIntent.ClosePicker)
                        viewModel.handle(DeviceBarIntent.RequestPairOverWifi)
                    },
                )
            }
        }
        if (state.isRefreshing) {
            LoadingIndicator(Modifier.padding(start = 4.dp).size(36.dp))
        } else {
            IconButton(onClick = { viewModel.handle(DeviceBarIntent.Refresh) }) { Icon(Icons.Filled.Refresh, "Refresh devices") }
        }
    }
}

private fun stateLabel(state: DeviceConnectionState): String = when (state) {
    DeviceConnectionState.Online -> "online"
    DeviceConnectionState.Offline -> "offline"
    else -> state.toString().substringBefore('(').lowercase()
}
