package io.github.raghavsatyadev.androidworld.desktop.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.components.FactTile
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsIntent
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsState
import io.github.raghavsatyadev.androidworld.core.domain.appdata.AppDetails
import io.github.raghavsatyadev.androidworld.core.domain.appdata.AppPermission
import io.github.raghavsatyadev.androidworld.core.domain.appdata.PermissionKind
import io.github.raghavsatyadev.androidworld.core.domain.appdata.PermissionState

@Composable
private fun DetailsGate(state: AppDetailsState, content: @Composable (AppDetails) -> Unit) {
    val details = state.details
    when {
        details != null -> content(details)
        state.detailsError != null -> EmptyState(state.detailsError!!, Icons.Outlined.ErrorOutline)
        else -> EmptyState("Reading package details…", loading = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoTab(state: AppDetailsState) = DetailsGate(state) { d ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard("Package") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val facts = listOf(
                    "Version name" to d.versionName,
                    "Version code" to d.versionCode,
                    "Min SDK" to d.minSdk,
                    "Target SDK" to d.targetSdk,
                    "UID" to d.uid,
                    "ABI" to d.primaryAbi,
                    "Installer" to d.installer,
                    "State" to d.isStopped?.let { if (it) "Stopped" else "Not stopped" },
                    "Installed" to d.firstInstallTime,
                    "Updated" to d.lastUpdateTime,
                )
                facts.forEach { (label, value) -> FactTile(label, value ?: "—", Modifier.widthIn(min = 150.dp)) }
            }
        }
        SectionCard("Paths") {
            Path("APK", d.codePath)
            Path("Data", d.dataDir)
        }
        if (d.flags.isNotEmpty()) SectionCard("Flags") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.flags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
        }
    }
}

@Composable
private fun Path(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 56.dp))
        Text(value ?: "—", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Runtime permissions get switches; install-time and signature ones are listed read-only. */
@Composable
fun PermissionsTab(state: AppDetailsState, onIntent: (AppDetailsIntent) -> Unit) = DetailsGate(state) { d ->
    if (d.permissions.isEmpty()) {
        EmptyState("This app requests no permissions", Icons.Outlined.Shield)
        return@DetailsGate
    }
    val groups = d.permissions.groupBy { it.kind }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d.permissions.count { it.granted == true }} of ${d.permissions.size} granted",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (d.permissions.any { it.resettable }) {
                    TextButton(onClick = { onIntent(AppDetailsIntent.ResetAllPermissions) }, enabled = state.permissionBusy == null) { Text("Reset all") }
                }
            }
        }
        PermissionKind.entries.forEach { kind ->
            val list = groups[kind].orEmpty()
            if (list.isEmpty()) return@forEach
            item(key = "h-$kind") {
                Text(
                    kind.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(list, key = { "${kind}-${it.name}" }) { permission ->
                PermissionRow(permission, busy = state.permissionBusy == permission.name, anyBusy = state.permissionBusy != null, onIntent, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun PermissionRow(p: AppPermission, busy: Boolean, anyBusy: Boolean, onIntent: (AppDetailsIntent) -> Unit, modifier: Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(p.name.substringAfterLast('.'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOf(p.name, p.state.label).joinToString("  ·  "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (busy) LoadingIndicator(Modifier.padding(end = 4.dp))
                if (p.resettable) {
                    TextButton(onClick = { onIntent(AppDetailsIntent.ResetPermission(p.name)) }, enabled = !anyBusy) { Text("Reset") }
                }
                if (p.mutable) {
                    Switch(
                        checked = p.granted == true,
                        enabled = !anyBusy,
                        onCheckedChange = { grant ->
                            onIntent(if (grant) AppDetailsIntent.GrantPermission(p.name) else AppDetailsIntent.RevokePermission(p.name))
                        },
                    )
                } else {
                    Text(if (p.granted == true) "Granted" else "Not granted", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}

private val PermissionKind.title: String
    get() = when (this) {
        PermissionKind.RUNTIME -> "Runtime"
        PermissionKind.INSTALL -> "Install-time"
        PermissionKind.SIGNATURE -> "Signature"
        PermissionKind.UNKNOWN -> "Other"
    }

private val PermissionState.label: String
    get() = when (this) {
        PermissionState.NOT_REQUESTED -> "Not requested"
        PermissionState.GRANTED -> "Granted"
        PermissionState.GRANTED_ONE_TIME -> "Granted once"
        PermissionState.DENIED -> "Denied"
        PermissionState.DENIED_PERMANENTLY -> "Denied permanently"
        PermissionState.FIXED_READ_ONLY -> "Fixed by policy"
        PermissionState.UNKNOWN -> "Unknown"
    }
