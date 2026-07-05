package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Gallery grid cell (DESIGN §6.7). Square (not 4:5) — the wrapper rounds at
 * [NpicShapes.md] (14dp).
 *
 * Signature indicator (bottom-right, offset -8dp from edges) — m2056 Item 1:
 * ALWAYS shown so the state is universally legible (previously the badge only
 * appeared when a signature was present, so users had to remember "absence
 * means missing" — DESIGN §6.7's original signal). Two variants:
 *   • Present: 24dp Saffron circle + 14dp Ivory `Draw` glyph.
 *   • Missing: 24dp inkMuted circle + 14dp Ivory `Draw` glyph + 2dp Terracotta
 *     diagonal strike-through line drawn across the circle. Matches the visual
 *     language of "banned/off" indicators (Wi-Fi off, camera off).
 *
 * Selected state (multi-select): 3dp Saffron ring outside the thumb + white check on a
 * Saffron circle top-right. Whole cell scales to 96%.
 *
 * The name/serial label and class sub-chip that DESIGN §6.7 places *below* the thumbnail
 * are rendered by the grid item wrapper, not this component. That keeps the thumb a pure
 * fixed-square unit that the grid can pack without worrying about caption height variance.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val reduceMotion = com.npic.photoandsignscanner.core.theme.LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.96f else 1f,
        animationSpec = NpicMotion.standardOrSnap(reduceMotion),
        label = "thumb_scale",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .let { m ->
                // DESIGN §6.7: selected state uses a 3dp Saffron ring OUTSIDE the thumb.
                // We render this by drawing the border BEFORE clipping so it sits around
                // the rounded square rather than clipping into it.
                if (selected) m.border(3.dp, NpicColors.Saffron, NpicShapes.md)
                else m
            }
            .clip(NpicShapes.md)
            .background(NpicColors.Surface, NpicShapes.md)
            .let { m ->
                if (!selected) m.border(1.dp, chrome.borderSoft, NpicShapes.md) else m
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        content()

        // Signature indicator (bottom-right) — m2056 Item 1: always present.
        // Draw glyph is Icons.Outlined.Draw (Material 3's "hand drawing a signature").
        // Was Icons.Outlined.Edit until m2354 Bug I — users mistook the pencil for an
        // edit affordance and tapped by accident.
        val badgeContainerColor = if (missingSignature) chrome.inkMuted else NpicColors.Saffron
        val badgeContentDescription =
            if (missingSignature) "No signature" else "Has signature"
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp, y = (-8).dp)
                .size(24.dp)
                .clip(NpicShapes.full)
                .background(badgeContainerColor, NpicShapes.full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Draw,
                contentDescription = badgeContentDescription,
                tint = NpicColors.Ivory,
                modifier = Modifier.size(14.dp),
            )
            if (missingSignature) {
                // Diagonal 2dp Terracotta strike-through across the circle. Drawn on a
                // full-sized Canvas so the line touches the circle rim on both ends,
                // matching the "no-symbol" visual language used across the app.
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color = NpicColors.Terracotta,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end   = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
        }

        // Selection check badge (top-end).
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(NpicSpacing.xs)
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(NpicShapes.full)
                    .background(NpicColors.Saffron, NpicShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
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
                NpicThumbnail("9",  Modifier.size(120.dp))
                NpicThumbnail("10", Modifier.size(120.dp), selected = true)
                NpicThumbnail("11", Modifier.size(120.dp), missingSignature = true)
            }
        }
    }
}
