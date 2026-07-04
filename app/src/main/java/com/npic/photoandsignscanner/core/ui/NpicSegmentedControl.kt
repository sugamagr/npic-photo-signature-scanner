package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Equal-width segmented picker. 48dp tall, `NpicShapes.md`, 1dp outer border. The active
 * segment fills with `SaffronSoft` and Ink label at weight 600; inactive segments are
 * transparent with InkMuted labels at weight 500.
 *
 * Used by:
 * - Save dialog Class picker (9 / 10 / 11 / 12)
 * - Save dialog Naming picker (Serial / Name)
 * - Rotate direction picker if we ever need one
 *
 * The [options] list may be any type; the caller provides a label mapper.
 */
@Composable
fun <T> NpicSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: (T) -> String,
    enabled: Boolean = true,
) {
    require(options.isNotEmpty()) { "NpicSegmentedControl requires at least one option" }
    val chrome = LocalNpicChrome.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(NpicShapes.sm)
            .background(NpicColors.Ivory, NpicShapes.sm)
            .border(1.dp, chrome.borderStrong, NpicShapes.sm)
            .padding(NpicSpacing.xxs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            // DESIGN §6.9: Selected segment fill Surface (not SaffronSoft), 1dp BorderStrong all around,
            // small shadow (Level 1). Unselected segments transparent.
            val container by animateColorAsState(
                targetValue = if (isSelected) NpicColors.Surface else Color.Transparent,
                animationSpec = NpicMotion.standard(),
                label = "segment_container",
            )
            val label by animateColorAsState(
                targetValue = when {
                    !enabled  -> chrome.inkFaint
                    isSelected -> NpicColors.Ink
                    else       -> chrome.inkMuted
                },
                animationSpec = NpicMotion.standard(),
                label = "segment_label",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(NpicShapes.xs)
                    .background(container, NpicShapes.xs)
                    .let { m ->
                        if (isSelected) m.border(1.dp, chrome.borderStrong, NpicShapes.xs) else m
                    }
                    .clickable(enabled = enabled) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = labelOf(option),
                    color = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight(600) else FontWeight(500),
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "SegmentedControl — class picker")
@Composable
private fun SegmentedPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.md).fillMaxWidth()) {
            NpicSegmentedControl(
                options  = listOf("9", "10", "11", "12"),
                selected = "10",
                onSelect = {},
                labelOf  = { "Class $it" },
            )
        }
    }
}
