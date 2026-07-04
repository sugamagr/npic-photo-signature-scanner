package com.npic.photoandsignscanner.features.edit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicSlider
import com.npic.photoandsignscanner.domain.model.RotationSpec

/**
 * Rotate tool (~140dp). Per DESIGN §7.3:
 *   • Row 1 (56dp): two 56dp × 56dp icon buttons — 90° CCW and 90° CW — centered with 32dp
 *     gap. No fill or border; SaffronSoft @ 22% ripple + 96% press scale.
 *   • 12dp gap.
 *   • Row 2 (48dp): Straighten slider bipolar -15°..+15°. Fixed-88dp label, flex slider,
 *     fixed-48dp Saffron readout ("+2.3°").
 */
@Composable
fun RotateTool(
    rotation: RotationSpec,
    onRotateCw: () -> Unit,
    onRotateCcw: () -> Unit,
    onStraightenChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(EditTool.Rotate.panelHeight)
            .padding(horizontal = NpicSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xxl, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuarterTurnButton(
                icon = Icons.Outlined.Rotate90DegreesCcw,
                contentDescription = "Rotate 90° counter-clockwise",
                onClick = onRotateCcw,
            )
            QuarterTurnButton(
                icon = Icons.Outlined.Rotate90DegreesCw,
                contentDescription = "Rotate 90° clockwise",
                onClick = onRotateCw,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(NpicSpacing.sm))
        StraightenRow(
            degrees = rotation.straightenDegrees,
            onChange = onStraightenChange,
        )
    }
}

@Composable
private fun QuarterTurnButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = NpicMotion.fastOrSnap(reduceMotion),
        label = "quarter_turn_scale",
    )
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = NpicColors.SaffronSoft.copy(alpha = 0.22f)),
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NpicColors.Saffron,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun StraightenRow(
    degrees: Float,
    onChange: (Float) -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = "Straighten",
            color = chrome.cameraInk,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(88.dp),
        )
        NpicSlider(
            label = "Straighten",
            value = degrees,
            onValueChange = onChange,
            valueRange = RotationSpec.STRAIGHTEN_MIN..RotationSpec.STRAIGHTEN_MAX,
            valueLabel = formatDegrees(degrees),
            onDark = true,
            showHeader = false,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = NpicSpacing.xs),
        )
        Text(
            text  = formatDegrees(degrees),
            color = NpicColors.Saffron,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

private fun formatDegrees(v: Float): String {
    val sign = if (v > 0f) "+" else if (v < 0f) "-" else ""
    val mag = kotlin.math.abs(v)
    val whole = mag.toInt()
    val tenth = ((mag - whole) * 10f).toInt().coerceIn(0, 9)
    return "$sign$whole.$tenth°"
}
