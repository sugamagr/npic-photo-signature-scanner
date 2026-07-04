package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
 * Gallery tile. 4:5 aspect (matches Combined export format) and rendered as a solid
 * placeholder box in this component — actual image loading is a Coil concern layered on top
 * by the Gallery feature. This component is responsible for:
 *
 * - Consistent shape ([NpicShapes.md]) across the grid
 * - Selection affordance (2dp Saffron border + check badge + scale-to-0.96)
 * - Missing-signature indicator strip (2dp Terracotta bottom border)
 * - Class corner badge
 *
 * The `content` slot lets the caller stack an AsyncImage on top when live.
 */
@Composable
fun NpicThumbnail(
    classLabel: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    missingSignature: Boolean = false,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(NpicColors.SurfaceRaised))
    },
) {
    val chrome = LocalNpicChrome.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.96f else 1f,
        animationSpec = NpicMotion.standard(),
        label = "thumb_scale",
    )

    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(NpicShapes.md)
            .background(NpicColors.Surface, NpicShapes.md)
            .clickable(onClick = onClick)
            .let { m ->
                if (selected) m.border(2.dp, NpicColors.Saffron, NpicShapes.md)
                else m.border(1.dp, chrome.borderSoft, NpicShapes.md)
            },
    ) {
        content()

        if (missingSignature) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(1.dp) // stay inside the shape's clip
                    .border(2.dp, chrome.terracotta, NpicShapes.md),
            )
        }

        // Class corner badge (top-start)
        Box(
            Modifier
                .padding(NpicSpacing.xs)
                .align(Alignment.TopStart)
                .clip(NpicShapes.full)
                .background(NpicColors.Ink.copy(alpha = 0.72f), NpicShapes.full)
                .padding(horizontal = NpicSpacing.xs, vertical = 2.dp),
        ) {
            Text(
                text = classLabel,
                color = NpicColors.Ivory,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(600)),
            )
        }

        // Selection check badge (top-end)
        if (selected) {
            Box(
                Modifier
                    .padding(NpicSpacing.xs)
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(NpicShapes.full)
                    .background(NpicColors.Saffron, NpicShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = NpicColors.Ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Thumbnails — states")
@Composable
private fun ThumbnailPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.md).size(400.dp, 200.dp)) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NpicSpacing.sm),
            ) {
                NpicThumbnail("9",  Modifier.size(120.dp, 150.dp))
                NpicThumbnail("10", Modifier.size(120.dp, 150.dp), selected = true)
                NpicThumbnail("11", Modifier.size(120.dp, 150.dp), missingSignature = true)
            }
        }
    }
}
