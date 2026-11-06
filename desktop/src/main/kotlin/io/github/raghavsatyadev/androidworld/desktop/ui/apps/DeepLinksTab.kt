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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsIntent
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsState
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.AppLinkVerification
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.DeepLinkVariant
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.DeepLinkVariants
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.DeviceLinkState
import io.github.raghavsatyadev.androidworld.core.domain.deeplinks.ParameterRequirement

/**
 * The app's deep links, read from its APK by `apkanalyzer`: every concrete variant with fields for
 * its open slots, plus App Link verification per host and a free-form URI box.
 */
@Composable
fun DeepLinksTab(state: AppDetailsState, onIntent: (AppDetailsIntent) -> Unit) {
    val analysis = state.deepLinks
    when {
        state.deepLinksLoading && analysis == null -> EmptyState("Reading the APK's intent filters…", loading = true)
        analysis == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Link, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                state.deepLinksError ?: "Find every link this app handles",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.deepLinksError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).widthIn(max = 520.dp),
            )
            Button(onClick = { onIntent(AppDetailsIntent.AnalyzeDeepLinks) }, shapes = ButtonDefaults.shapes()) { Text("Analyze deep links") }
            FreeformLink(onIntent, Modifier.padding(top = 24.dp).widthIn(max = 560.dp))
        }
        else -> {
            val variants = remember(analysis) { analysis.catalog.targets.flatMap(DeepLinkVariants::expand) }
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${variants.size} links in ${analysis.catalog.targets.size} activities" + if (analysis.fromCache) " · cached" else "",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.deepLinksLoading) LoadingIndicator(Modifier.size(32.dp))
                        Hint("Analyze again") { IconButton(onClick = { onIntent(AppDetailsIntent.AnalyzeDeepLinks) }) { Icon(Icons.Filled.Refresh, "Analyze again") } }
                    }
                }
                item { FreeformLink(onIntent, Modifier.fillMaxWidth()) }
                if (analysis.verifications.isNotEmpty()) item { Verifications(analysis.verifications) }
                if (variants.isEmpty()) item { Text("No intent filter takes a VIEW link.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(variants, key = { "${it.componentName}|${it.template}" }) { variant ->
                    VariantCard(variant, onIntent, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun FreeformLink(onIntent: (AppDetailsIntent) -> Unit, modifier: Modifier) {
    var uri by rememberSaveable { mutableStateOf("") }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uri, onValueChange = { uri = it },
            label = { Text("Any URI") }, singleLine = true,
            modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large,
        )
        FilledTonalButton(onClick = { onIntent(AppDetailsIntent.OpenDeepLink(uri.trim())) }, enabled = uri.isNotBlank(), shapes = ButtonDefaults.shapes()) {
            Text("Open")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Verifications(list: List<AppLinkVerification>) {
    SectionCard("App Link verification") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { v ->
                SuggestionChip(
                    onClick = {},
                    icon = if (v.deviceState == DeviceLinkState.VERIFIED) ({ Icon(Icons.Outlined.Verified, null) }) else null,
                    label = { Text("${v.host} · ${v.deviceState.name.lowercase().replace('_', ' ')}" + (v.error?.let { " · $it" } ?: "")) },
                )
            }
        }
    }
}

@Composable
private fun VariantCard(variant: DeepLinkVariant, onIntent: (AppDetailsIntent) -> Unit, modifier: Modifier) {
    val values = remember(variant) { mutableStateMapOf<String, String>().apply { variant.parameters.forEach { p -> p.defaultValue?.let { put(p.name, it) } } } }
    val optional = remember(variant) { variant.parameters.filter { it.requirement != ParameterRequirement.REQUIRED }.map { it.name }.toSet() }
    val built = DeepLinkVariants.build(variant.template, values.toMap(), emptyList(), optional)
    SectionCard(
        title = variant.componentName.substringAfterLast('.'),
        modifier = modifier,
        trailing = {
            if (variant.autoVerify) Hint("autoVerify App Link") { Icon(Icons.Outlined.Verified, "App Link", tint = MaterialTheme.colorScheme.primary) }
        },
    ) {
        Text(variant.template, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        variant.advancedPattern?.let {
            Text("Matches the regex $it; check the built URI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        variant.parameters.forEach { p ->
            OutlinedTextField(
                value = values[p.name].orEmpty(),
                onValueChange = { values[p.name] = it },
                label = { Text(p.name + if (p.requirement == ParameterRequirement.REQUIRED) " *" else "") },
                supportingText = p.type?.let { { Text("${p.location.name.lowercase()} · $it") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(built.uri, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Button(onClick = { onIntent(AppDetailsIntent.OpenDeepLink(built.uri)) }, enabled = built.missing.isEmpty(), shapes = ButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize))
                Text("Open", Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}
