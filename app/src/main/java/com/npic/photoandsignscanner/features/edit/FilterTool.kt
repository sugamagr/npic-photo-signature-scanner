package com.npic.photoandsignscanner.features.edit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.domain.model.FilterPreset

/**
 * Filter tool (~120dp). Horizontal scrollable strip of 8 preset thumbnails per DESIGN §7.3.
 * Each cell = 84dp square image at [NpicShapes.sm] + labelMedium label below (single line,
 * ellipsize). Selected cell gets a 2dp Saffron ring OUTSIDE the corner + label weight 700 +
 * a 12dp Saffron checkmark top-right.
 *
 * TODO(pipeline): GPU-rendered live preview lands in Layer 7b. For now, each cell paints a
 * flat tinted placeholder so the strip is visually and semantically complete, and users can
 * still pick + persist a preset in state.
 */
@Composable
fun FilterTool(
    selected: FilterPreset,
    onSelect: (FilterPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EditTool.Filter.panelHeight)
            .horizontalScroll(scroll)
            .padding(PaddingValues(start = NpicSpacing.md, end = NpicSpacing.md)),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterPreset.entries.forEach { preset ->
            FilterCell(
                preset = preset,
                isSelected = preset == selected,
                onClick = { onSelect(preset) },
            )
        }
    }
}

@Composable
private fun FilterCell(
    preset: FilterPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) NpicColors.Saffron else Color.Transparent,
        animationSpec = NpicMotion.fast(),
        label = "filter_cell_ring",
    )
    Column(
        modifier = Modifier
            .width(84.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = isSelected
                contentDescription = preset.label
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(NpicShapes.sm)
                .background(placeholderTintFor(preset), NpicShapes.sm)
                .border(2.dp, ringColor, NpicShapes.sm),
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(NpicSpacing.xxs)
                        .size(16.dp)
                        .background(NpicColors.Saffron, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = NpicColors.Ink,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(NpicSpacing.xxs))
        Text(
            text = preset.label,
            color = chrome.cameraInk,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight(700) else FontWeight(500),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Flat placeholder colors distinguishing the 8 presets visually until Layer 7b wires the
 * live GPU preview. Colors chosen to be evocative of each preset's target tone curve, not
 * an accurate simulation.
 */
private fun placeholderTintFor(preset: FilterPreset): Color = when (preset) {
    FilterPreset.Auto        -> NpicColors.Saffron.copy(alpha = 0.35f)
    FilterPreset.Original    -> Color(0xFFB0B0B0)
    FilterPreset.ColorBoost  -> Color(0xFFE07B39)
    FilterPreset.DocumentBw  -> Color(0xFFE8E4DA)
    FilterPreset.Passport    -> Color(0xFFC7B58A)
    FilterPreset.SchoolId    -> Color(0xFFB09A6E)
    FilterPreset.FadedRescue -> Color(0xFFD9C2A0)
    FilterPreset.InkBoost    -> Color(0xFF2E2E2E)
}
