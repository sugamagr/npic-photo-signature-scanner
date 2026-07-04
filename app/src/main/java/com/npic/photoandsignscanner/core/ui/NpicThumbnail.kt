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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Signature indicator (bottom-right, offset -8dp from edges): 24dp Saffron circle with a
 * 14dp signature glyph in white, shown ONLY when the record has a signature. Absence of the
 * indicator is itself the "missing signature" signal (per DESIGN §6.7 + PRD §4.8).
 *
 * Selected state (multi-select): 3dp Saffron ring outside the thumb + white check on a
 * Saffron circle top-right. Whole cell scales to 96%.
 *
 * The name/serial label and class sub-chip that DESIGN §6.7 places *below* the thumbnail
 * are rendered by the grid item wrapper, not this component. That keeps the thumb a pure
 * fixed-square unit that the grid can pack without worrying about caption height variance.
 * TODO(gallery): promote label + sub-chip into an `NpicThumbnailCard` when Detail lands.
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
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.96f else 1f,
        animationSpec = NpicMotion.standard(),
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

        // Signature indicator (bottom-right, DESIGN §6.7): Saffron 24dp circle with a
        // white 14dp signature glyph. Shown only when signature exists.
        if (!missingSignature) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-8).dp, y = (-8).dp)
                    .size(24.dp)
                    .clip(NpicShapes.full)
                    .background(NpicColors.Saffron, NpicShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Has signature",
                    tint = NpicColors.Ivory,
                    modifier = Modifier.size(14.dp),
                )
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
                NpicThumbnail("9",  Modifier.size(120.dp))
                NpicThumbnail("10", Modifier.size(120.dp), selected = true)
                NpicThumbnail("11", Modifier.size(120.dp), missingSignature = true)
            }
        }
    }
}
