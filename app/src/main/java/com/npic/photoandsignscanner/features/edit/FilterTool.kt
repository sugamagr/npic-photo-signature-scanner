package com.npic.photoandsignscanner.features.edit

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
 * Thumbnails are 192px cached bitmaps rendered by [EditViewModel] against the user's current
 * source (DESIGN §6.18 max working res). When a preset's thumbnail hasn't finished rendering
 * yet — or when the ViewModel is still decoding the source — the cell shows a neutral
 * CameraSurface placeholder so the strip stays visually stable and the RadioButton
 * semantics/selection state remain intact.
 */
@Composable
fun FilterTool(
    selected: FilterPreset,
    onSelect: (FilterPreset) -> Unit,
    thumbnails: Map<FilterPreset, Bitmap>,
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
                thumbnail = thumbnails[preset],
            )
        }
    }
}

@Composable
private fun FilterCell(
    preset: FilterPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    thumbnail: Bitmap?,
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
                .background(chrome.cameraSurface, NpicShapes.sm)
                .border(2.dp, ringColor, NpicShapes.sm),
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(NpicShapes.sm),
                )
            }
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
        Spacer(Modifier.height(NpicSpacing.xxs))
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
