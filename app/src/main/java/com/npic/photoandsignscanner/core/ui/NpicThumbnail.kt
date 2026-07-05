package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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
 * means missing" — DESIGN §6.7's original signal). Two variants using the
 * industry-standard filled-vs-outlined ring pattern (m2410 rewrite; see the
 * body comment for the full precedent list and rejected earlier attempts):
 *   • Present: 24dp filled Saffron circle + 14dp Ivory `Draw` glyph.
 *   • Missing: 24dp Ivory (Surface) circle with a 2dp Saffron ring around
 *     it + 14dp Saffron `Draw` glyph inside.
 * Present reads as "loud and saturated"; missing reads as "quiet outline".
 * No strike-through, no error semantics.
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
    // m2334 Fix #2: drag-to-select Pattern A. When non-null, the thumbnail
    // forwards drag events AFTER a long-press without lifting — the same
    // touch stream that started the long-press becomes a continuous drag,
    // matching Samsung Gallery / Google Photos semantics. The offset param
    // is in WINDOW coordinates so the grid handler can hit-test against
    // its own cellBounds map without needing per-cell coordinate math.
    onDragToWindowOffset: ((Offset) -> Unit)? = null,
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

    // Cache the thumbnail's own window-space top-left so the drag detector can
    // translate its LOCAL pointer positions into window coordinates for the
    // grid handler.
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }

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
            .let { m ->
                if (onDragToWindowOffset != null) {
                    m
                        .onGloballyPositioned { coords ->
                            windowOrigin = coords.positionInWindow()
                        }
                        .pointerInput(Unit) {
                            // Long-press-then-drag detector. The long-press fires
                            // onLongPress (which enables selection mode + toggles
                            // this cell), THEN as the finger continues to move
                            // without lifting, onDrag forwards each new pointer
                            // position in window coordinates upstream.
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    onLongPress()
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onDragToWindowOffset(windowOrigin + change.position)
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            // Separate click detector so single-tap still fires
                            // onClick (detectTapGestures doesn't interfere with
                            // detectDragGesturesAfterLongPress because Compose
                            // routes long-press and single-tap on different
                            // gesture-arbitration paths).
                            detectTapGestures(
                                onTap = { onClick() },
                            )
                        }
                } else {
                    m.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                }
            },
    ) {
        content()

        // Signature indicator (bottom-right) — m2056 Item 1: always present.
        //
        // m2410 rewrite: industry-standard "filled vs outlined" two-state pattern
        // (Instagram highlight ring, WhatsApp read receipts, iOS Contacts field
        // presence). No strike-through, no color swap — just fill vs ring.
        //
        //   • Present: solid Saffron circle + Ivory Draw glyph inside
        //   • Missing: 2 dp Saffron ring around a Surface (Ivory) fill + Saffron
        //     Draw glyph inside
        //
        // Both variants use the same 24 dp size, same glyph, same anchor. The
        // fill-vs-ring distinction is universally readable: present state is
        // "loud and saturated", missing state is "quiet outline". Nothing about
        // it reads as error or alarm.
        //
        // Historical note: the earlier Terracotta strike-through (m2278) and Ivory
        // strike-through (m2334) both invented a novel indicator that no shipping
        // app uses. Users read it as damaged or errored rather than "not yet".
        // Draw glyph is Icons.Outlined.Draw (Material 3's "hand drawing a signature")
        // — was Icons.Outlined.Edit until m2354 Bug I (users mistook pencil for edit).
        val badgeContentDescription =
            if (missingSignature) "No signature" else "Has signature"
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp, y = (-8).dp)
                .size(24.dp)
                .clip(NpicShapes.full)
                .background(
                    color = if (missingSignature) NpicColors.Surface else NpicColors.Saffron,
                    shape = NpicShapes.full,
                )
                .let { m ->
                    if (missingSignature) m.border(2.dp, NpicColors.Saffron, NpicShapes.full)
                    else m
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Draw,
                contentDescription = badgeContentDescription,
                tint = if (missingSignature) NpicColors.Saffron else NpicColors.Ivory,
                modifier = Modifier.size(14.dp),
            )
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
