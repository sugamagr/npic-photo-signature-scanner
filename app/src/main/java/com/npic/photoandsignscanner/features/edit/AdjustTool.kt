package com.npic.photoandsignscanner.features.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Deblur
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicSlider
import com.npic.photoandsignscanner.domain.model.Adjustments

/**
 * Adjust tool (~200dp). Five 40dp rows separated by 4dp gaps. Each row:
 *   `[20dp icon] [8dp gap] [88dp label] [flex slider onDark] [32dp value, right-aligned]`
 *
 * All five channels are bipolar `-50..+50`. Per DESIGN §7.3 the value column uses tabular
 * lining digits; MaterialTheme's labelMedium (Inter) supports tabular figures.
 */
@Composable
fun AdjustTool(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(EditTool.Adjust.panelHeight)
            .padding(horizontal = NpicSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
    ) {
        AdjustRow(
            icon  = Icons.Outlined.Brightness6,
            label = "Brightness",
            value = adjustments.brightness,
            onChange = { onAdjustmentsChange(adjustments.copy(brightness = it)) },
        )
        AdjustRow(
            icon  = Icons.Outlined.Contrast,
            label = "Contrast",
            value = adjustments.contrast,
            onChange = { onAdjustmentsChange(adjustments.copy(contrast = it)) },
        )
        AdjustRow(
            icon  = Icons.Outlined.Deblur,
            label = "Sharpness",
            value = adjustments.sharpness,
            onChange = { onAdjustmentsChange(adjustments.copy(sharpness = it)) },
        )
        AdjustRow(
            icon  = Icons.Outlined.InvertColors,
            label = "Saturation",
            value = adjustments.saturation,
            onChange = { onAdjustmentsChange(adjustments.copy(saturation = it)) },
        )
        AdjustRow(
            icon  = Icons.Outlined.Thermostat,
            label = "Warmth",
            value = adjustments.warmth,
            onChange = { onAdjustmentsChange(adjustments.copy(warmth = it)) },
        )
    }
}

@Composable
private fun AdjustRow(
    icon: ImageVector,
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        // BLOCKER B-7a-2: hard .height(40.dp) clips labelMedium at 200% font scale
        // (14sp → 28sp needs ~37dp line height). heightIn(min = 40.dp) preserves the
        // spec baseline while allowing expansion for accessibility scaling.
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = chrome.cameraInk,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text  = label,
            color = chrome.cameraInk,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(start = NpicSpacing.xs)
                .width(88.dp),
        )
        NpicSlider(
            label = label,
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(Adjustments.MIN, Adjustments.MAX)) },
            valueRange = Adjustments.MIN.toFloat()..Adjustments.MAX.toFloat(),
            valueLabel = formatSigned(value),
            onDark = true,
            showHeader = false,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = NpicSpacing.xs),
        )
        Text(
            text  = formatSigned(value),
            color = chrome.cameraInk,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
    }
}

private fun formatSigned(v: Int): String = when {
    v > 0 -> "+$v"
    else  -> v.toString()
}
