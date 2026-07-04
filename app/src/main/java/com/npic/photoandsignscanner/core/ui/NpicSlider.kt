package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Adjustment slider. Row layout with left-aligned label, right-aligned value chip, and the
 * track below spanning full width.
 *
 * Used by Edit → Adjust tab (Brightness, Contrast, Sharpness, Saturation, Warmth — each -50
 * to +50), and by Signature Draw (pen thickness 2–12).
 *
 * The `onDark` variant swaps track/thumb/label colors for the dark camera chrome (Edit ⇢
 * Adjust, Signature Draw).
 */
@Composable
fun NpicSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueLabel: String = value.toInt().toString(),
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    onDark: Boolean = false,
) {
    val chrome = LocalNpicChrome.current
    val labelColor = if (onDark) chrome.cameraInk else NpicColors.Ink
    val valueColor = if (onDark) chrome.cameraInkMuted else chrome.inkMuted
    val trackActive   = NpicColors.Saffron
    val trackInactive = if (onDark) chrome.cameraInkMuted.copy(alpha = 0.35f) else chrome.borderStrong
    val thumbColor    = NpicColors.Saffron

    Column(modifier = modifier.fillMaxWidth().padding(vertical = NpicSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = label,
                color = labelColor,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text  = valueLabel,
                color = valueColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor              = thumbColor,
                activeTrackColor        = trackActive,
                inactiveTrackColor      = trackInactive,
                disabledThumbColor      = chrome.inkFaint,
                disabledActiveTrackColor = chrome.borderStrong,
                disabledInactiveTrackColor = chrome.borderSoft,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Slider — adjust panel (light)")
@Composable
private fun SliderPreviewLight() {
    NpicTheme {
        var v = 12f
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.md).fillMaxWidth()) {
            NpicSlider(
                label = "Brightness",
                value = v,
                onValueChange = { v = it },
                valueRange = -50f..50f,
                valueLabel = "+12",
            )
        }
    }
}

@Preview(name = "Slider — dark (camera)")
@Composable
private fun SliderPreviewDark() {
    NpicTheme {
        val chrome = LocalNpicChrome.current
        var v = -8f
        Box(Modifier.background(chrome.cameraBg).padding(NpicSpacing.md).fillMaxWidth()) {
            NpicSlider(
                label = "Contrast",
                value = v,
                onValueChange = { v = it },
                valueRange = -50f..50f,
                valueLabel = "-8",
                onDark = true,
            )
        }
    }
}
