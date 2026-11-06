package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.display.density.DensityIntent
import io.github.raghavsatyadev.androidworld.core.application.display.density.DensityViewState
import io.github.raghavsatyadev.androidworld.core.application.display.fontscale.FontScaleIntent
import io.github.raghavsatyadev.androidworld.core.domain.display.density.DensityPresets
import io.github.raghavsatyadev.androidworld.core.domain.display.density.DensityReading
import io.github.raghavsatyadev.androidworld.core.domain.display.fontscale.FontScalePresets
import io.github.raghavsatyadev.androidworld.core.domain.display.fontscale.FontScaleRange
import io.github.raghavsatyadev.androidworld.core.domain.display.fontscale.FontScaleState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun FontScaleCard(state: FontScaleState, onIntent: (FontScaleIntent) -> Unit) {
    val current = when (state) {
        FontScaleState.Loading -> null
        is FontScaleState.Idle -> state.current
        is FontScaleState.Applying -> state.current
        is FontScaleState.Error -> state.current
    }
    val busy = state is FontScaleState.Loading || state is FontScaleState.Applying
    SectionCard("Font scale", trailing = { RefreshButton { onIntent(FontScaleIntent.Retry) } }) {
        Text(
            when (state) {
                is FontScaleState.Applying -> "Setting ${scaleText(state.target)}…"
                else -> current?.let { "Now ${scaleText(it)}" } ?: "Reading…"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        ChoiceGroup(
            options = FontScalePresets.VALUES,
            selected = FontScalePresets.VALUES.firstOrNull { current != null && abs(it - current) < 0.001 },
            label = ::scaleText,
            onSelect = { onIntent(FontScaleIntent.Apply(it)) },
            enabled = !busy,
        )
        CustomValueRow(
            label = "Custom (${FontScaleRange.MIN}–${FontScaleRange.MAX})",
            enabled = !busy,
            parse = String::toDoubleOrNull,
            onApply = { onIntent(FontScaleIntent.Apply(it)) },
            onReset = { onIntent(FontScaleIntent.Reset) },
            resetEnabled = !busy && current != null && abs(current - FontScalePresets.DEFAULT) > 0.001,
        )
        ErrorText((state as? FontScaleState.Error)?.message)
    }
}

@Composable
internal fun DensityCard(state: DensityViewState, onIntent: (DensityIntent) -> Unit) {
    val reading = when (state) {
        DensityViewState.Loading -> null
        is DensityViewState.Idle -> state.reading
        is DensityViewState.Applying -> state.reading
        is DensityViewState.Error -> state.reading
    }
    val busy = state is DensityViewState.Loading || state is DensityViewState.Applying
    SectionCard("Screen density", trailing = { RefreshButton { onIntent(DensityIntent.Retry) } }) {
        Text(
            when {
                state is DensityViewState.Applying -> "Applying…"
                reading == null -> "Reading…"
                else -> densityText(reading)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        ChoiceGroup(
            options = DensityPresets.PERCENTAGES,
            selected = reading?.let { r -> DensityPresets.PERCENTAGES.firstOrNull { percentDpi(r.physicalDpi, it) == r.effective } },
            label = { "$it%" },
            onSelect = { onIntent(DensityIntent.ApplyPreset(it)) },
            enabled = !busy && reading != null,
        )
        CustomValueRow(
            label = reading?.let {
                "Custom dpi (${percentDpi(it.physicalDpi, DensityPresets.SAFE_RANGE_MIN_PERCENT)}–" +
                    "${percentDpi(it.physicalDpi, DensityPresets.SAFE_RANGE_MAX_PERCENT)})"
            } ?: "Custom dpi",
            enabled = !busy && reading != null,
            parse = String::toIntOrNull,
            onApply = { onIntent(DensityIntent.ApplyCustom(it)) },
            onReset = { onIntent(DensityIntent.Reset) },
            resetEnabled = !busy && reading?.overrideDpi != null,
        )
        ErrorText((state as? DensityViewState.Error)?.message)
    }
}

/** A number field with Apply, and a Reset to the device default. Validation is the view model's. */
@Composable
private fun <T : Any> CustomValueRow(
    label: String,
    enabled: Boolean,
    parse: (String) -> T?,
    onApply: (T) -> Unit,
    onReset: () -> Unit,
    resetEnabled: Boolean,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val value = parse(text.trim())
    val apply = { value?.let(onApply); Unit }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it }, label = { Text(label) }, singleLine = true, enabled = enabled,
            isError = text.isNotBlank() && value == null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply() }),
            modifier = Modifier.width(240.dp),
        )
        FilledTonalButton(onClick = apply, enabled = enabled && value != null) { Text("Apply") }
        OutlinedButton(onClick = onReset, enabled = resetEnabled) { Text("Reset") }
    }
}

private val DensityReading.effective: Int get() = overrideDpi ?: physicalDpi

private fun percentDpi(physical: Int, percent: Int): Int = (physical * percent / 100.0).roundToInt()

private fun densityText(reading: DensityReading): String =
    if (reading.overrideDpi == null) "${reading.physicalDpi} dpi (device default)"
    else "${reading.overrideDpi} dpi, ${(reading.overrideDpi!! * 100.0 / reading.physicalDpi).roundToInt()}% of ${reading.physicalDpi}"

private fun scaleText(value: Double): String = "${"%.2f".format(value).trimEnd('0').trimEnd('.')}×"
