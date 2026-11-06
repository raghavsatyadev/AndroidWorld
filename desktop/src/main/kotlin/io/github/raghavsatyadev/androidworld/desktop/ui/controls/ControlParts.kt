package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.display.QuickToggleFieldState
import io.github.raghavsatyadev.androidworld.core.application.display.valueOrNull

/** A scrolling column of cards, capped to a readable width. */
@Composable
internal fun ControlsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

/** Shown in place of a page's controls when no device can take commands. */
@Composable
internal fun NoDevice(text: String = "Pick a connected device to change its settings.") {
    EmptyState(text, loading = false, modifier = Modifier.padding(top = 96.dp))
}

/**
 * A labelled switch bound to a device readback. The switch shows the best known value, is disabled
 * while loading or applying (with a small loading indicator), and an error appears under the label.
 */
@Composable
internal fun FieldSwitch(
    label: String,
    field: QuickToggleFieldState<Boolean>,
    onChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    val busy = field is QuickToggleFieldState.Loading || field is QuickToggleFieldState.Applying
    Row(Modifier.fillMaxWidth().animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FieldError(field)
        }
        AnimatedVisibility(busy, enter = reveal(), exit = conceal()) {
            LoadingIndicator(Modifier.padding(end = 8.dp).size(24.dp))
        }
        Switch(checked = field.valueOrNull() == true, onCheckedChange = onChange, enabled = enabled && !busy)
    }
}

/** The error under a field, revealed when it appears. */
@Composable
internal fun FieldError(field: QuickToggleFieldState<*>) {
    ErrorText((field as? QuickToggleFieldState.Error)?.message)
}

@Composable
internal fun ErrorText(message: String?) {
    AnimatedVisibility(message != null, enter = reveal(), exit = conceal()) {
        Text(message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * An M3E connected button group: one choice of [options], [selected] highlighted (none when the
 * device reports something that isn't an option). Wraps when the row is too narrow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceGroup(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { if (option != selected) onSelect(option) },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label(option)) }
        }
    }
}
