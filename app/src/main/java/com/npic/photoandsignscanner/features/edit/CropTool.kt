package com.npic.photoandsignscanner.features.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.domain.model.AspectLock

/**
 * Crop tool content (~64dp). Left: ghost "Reset crop" (returns crop bounds to the initial
 * quad — full-image for Photo Picker imports, guide-box for fresh Camera captures — after
 * auto-detect was removed per m2154). Right: aspect chip row `Free · 3:4 · 3:1` per
 * DESIGN §7.3. Renders directly on CameraBg — no panel fill, no border, no hairline.
 * Horizontal 16dp padding only.
 */
@Composable
fun CropTool(
    aspectLock: AspectLock,
    onResetCrop: () -> Unit,
    onAspectLockChange: (AspectLock) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EditTool.Crop.panelHeight)
            .padding(horizontal = NpicSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ResetChip(onClick = onResetCrop)
        AspectChipRow(
            selected = aspectLock,
            onSelect = onAspectLockChange,
        )
    }
}

@Composable
private fun ResetChip(onClick: () -> Unit) {
    // BLOCKER B-7a-1: WCAG 2.5.5 requires 44dp minimum touch target; hardcoded 40dp fails.
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(NpicShapes.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = NpicSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = "Reset crop",
            color = NpicColors.Saffron,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
        )
    }
}

@Composable
private fun AspectChipRow(
    selected: AspectLock,
    onSelect: (AspectLock) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
    ) {
        AspectLock.ChipRowOptions.forEach { option ->
            AspectChip(
                label     = option.chipLabel,
                isSelected = option == selected,
                onClick   = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun AspectChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val container = if (isSelected) NpicColors.SaffronSoft.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent
    val border    = if (isSelected) NpicColors.Saffron else chrome.cameraInkMuted.copy(alpha = 0.40f)
    val ink       = if (isSelected) NpicColors.Saffron else chrome.cameraInkMuted
    val weight    = if (isSelected) FontWeight(600) else FontWeight(500)

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = isSelected
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(NpicShapes.xs)
                .background(container, NpicShapes.xs)
                .border(1.dp, border, NpicShapes.xs)
                .padding(horizontal = NpicSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = label,
                color = ink,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = weight),
            )
        }
    }
}

private val AspectLock.chipLabel: String
    get() = when (this) {
        AspectLock.Free       -> "Free"
        is AspectLock.Ratio   -> "$width:$height"
    }
