package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.apps.Hint
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.BannerAction
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.BannerTone
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeepLinkLauncherIntent
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeepLinkLauncherState
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.HandlersRow
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.HandlersState
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.LaunchBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Who handles this link?", answered by the device's resolver a moment after typing stops. */
@Composable
internal fun HandlersLine(state: DeepLinkLauncherState) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        when (val handlers = state.handlers) {
            HandlersState.Hidden -> Unit
            HandlersState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadingIndicator(Modifier.size(20.dp))
                Caption("Checking which app opens this link…")
            }
            is HandlersState.Ready -> {
                val row = HandlersRow.of(handlers.handlers, state.packageName, state::label)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(toneColor(row.tone), CircleShape))
                    Text(row.text, style = MaterialTheme.typography.bodyMedium)
                    row.handler?.let {
                        Text(state.label(it.substringBefore('/')), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    row.link?.let { TextButton(onClick = { showAll = !showAll }) { Text(it.removeSuffix(" ▾")) } }
                }
                AnimatedVisibility(showAll && row.link != null, enter = reveal(), exit = conceal()) {
                    Column(Modifier.padding(start = 18.dp)) {
                        handlers.handlers.matches.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }
    }
}

/** The result of the last Open, in the tone of how it went, with its follow-up action. */
@Composable
internal fun BannerCard(
    banner: LaunchBanner,
    label: (String) -> String,
    onIntent: (DeepLinkLauncherIntent) -> Unit,
    copy: (String, String) -> Unit,
) {
    val (container, content) = when (banner.tone) {
        BannerTone.Success -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BannerTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BannerTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(banner.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                banner.appPackage?.let(label)?.takeUnless { it in banner.title }?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge)
                }
                if (banner.detail.isNotBlank()) Text(banner.detail, style = MaterialTheme.typography.bodyMedium)
                banner.mono?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                when (val action = banner.action) {
                    is BannerAction.CopyVerifyCommand ->
                        TextButton(onClick = { copy(action.command, action.toast) }) { Text("Copy verify command") }
                    BannerAction.TurnOnColdStart ->
                        TextButton(onClick = { onIntent(DeepLinkLauncherIntent.SetColdStart(true)) }) { Text("Turn on cold start") }
                    null -> Unit
                }
            }
            IconButton(onClick = { onIntent(DeepLinkLauncherIntent.DismissResult) }) { Icon(Icons.Filled.Close, "Dismiss") }
        }
    }
}

/** Links opened before, newest first. A click fills the form; play opens it again. */
@Composable
internal fun LinkHistory(state: DeepLinkLauncherState, onIntent: (DeepLinkLauncherIntent) -> Unit) {
    SectionCard("History") {
        if (state.history.isEmpty()) {
            Caption("Links you open appear here.")
            return@SectionCard
        }
        Column(Modifier.animateContentSize()) {
            state.history.forEach { entry ->
                ListItem(
                    headlineContent = {
                        Text(entry.uri, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        val target = entry.handledBy.ifBlank { entry.packageName }
                        Text(listOfNotNull(target.takeIf(String::isNotBlank)?.let(state::label), whenText(entry.lastRun)).joinToString(" · "))
                    },
                    trailingContent = {
                        Row {
                            Hint("Open again") {
                                IconButton(onClick = { onIntent(DeepLinkLauncherIntent.Rerun(entry)) }, enabled = state.serial != null && !state.launch.busy) {
                                    Icon(Icons.Filled.PlayArrow, "Open again")
                                }
                            }
                            Hint("Remove from history") {
                                IconButton(onClick = { onIntent(DeepLinkLauncherIntent.Remove(entry)) }) { Icon(Icons.Filled.Delete, "Remove") }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onIntent(DeepLinkLauncherIntent.Reuse(entry)) },
                )
            }
        }
    }
}

@Composable
private fun toneColor(tone: BannerTone): Color = when (tone) {
    BannerTone.Success -> MaterialTheme.colorScheme.primary
    BannerTone.Warning -> MaterialTheme.colorScheme.tertiary
    BannerTone.Error -> MaterialTheme.colorScheme.error
}

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private fun whenText(millis: Long): String? =
    millis.takeIf { it > 0 }?.let { WHEN_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
