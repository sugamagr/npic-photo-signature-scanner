package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicElevation
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * The persistent Gallery Capture FAB. NOT a Material FAB — it's a wide rounded square:
 * 72dp tall, width `min(240dp, 60% of screen width)`, corner radius `NpicShapes.lg` (20dp),
 * Saffron fill, level-3 shadow, 28dp camera icon + "Capture" label (Ink, weight 600).
 *
 * DESIGN §7.17. The Home screen is Gallery + this FAB — no separate Home.
 */
@Composable
fun NpicCaptureFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Capture",
    contentDescription: String = "Capture new record",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "capturefab_scale",
    )

    BoxWithConstraints(modifier = modifier) {
        val screenW = maxWidth
        val target  = minOf(240.dp, screenW * 0.60f)

        Box(
            modifier = Modifier
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                }
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .width(target)
                .height(72.dp)
                .shadow(NpicElevation.level3, NpicShapes.lg, clip = false)
                .clip(NpicShapes.lg)
                .background(NpicColors.Saffron, NpicShapes.lg)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = NpicColors.Ink),
                    onClick = onClick,
                )
                .padding(horizontal = NpicSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = NpicColors.Ink,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text  = label,
                    color = NpicColors.Ink,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "CaptureFab")
@Composable
private fun CaptureFabPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.xl)) {
            NpicCaptureFab(onClick = {})
        }
    }
}
