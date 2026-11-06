package io.github.raghavsatyadev.androidworld.desktop.ui.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ChoiceGroup
import io.github.raghavsatyadev.androidworld.core.domain.commands.Choice
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandField
import io.github.raghavsatyadev.androidworld.core.domain.commands.FieldKind

/** Few choices show as a connected button group; more (or an optional one) as a dropdown. */
private const val MAX_BUTTON_CHOICES = 4

/** One typed field of a command: choices, or a text field that shows its problem in place of its hint. */
@Composable
internal fun CommandFieldInput(field: CommandField, value: String, problem: String?, onValue: (String) -> Unit) {
    when (val kind = field.kind) {
        is FieldKind.Choices -> if (kind.options.size <= MAX_BUTTON_CHOICES && !field.optional) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(field.label, style = MaterialTheme.typography.labelLarge)
                ChoiceGroup(kind.options, kind.options.firstOrNull { it.value == value }, Choice::label, { onValue(it.value) })
            }
        } else {
            ChoiceDropdown(field, kind.options, value, onValue)
        }
        else -> OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(if (field.optional) "${field.label} (optional)" else field.label) },
            supportingText = (problem ?: field.hint.ifEmpty { null })?.let { { Text(it) } },
            isError = problem != null && value.isNotEmpty(),
            singleLine = true,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxWidth(),
        )
    }
}

/** A read-only field that opens a menu of [options]; the overlay makes the whole field clickable. */
@Composable
private fun ChoiceDropdown(field: CommandField, options: List<Choice>, value: String, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val all = if (field.optional) listOf(Choice("", "Any")) + options else options
    Box(Modifier.widthIn(min = 280.dp, max = 560.dp)) {
        OutlinedTextField(
            value = all.firstOrNull { it.value == value }?.label ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(field.label) },
            supportingText = field.hint.ifEmpty { null }?.let { { Text(it) } },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            all.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.label) }, onClick = { onValue(choice.value); expanded = false })
            }
        }
    }
}
