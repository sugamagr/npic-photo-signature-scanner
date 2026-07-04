package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * A single filter chip in the Edit ⇢ Filter strip. 88dp wide × ~120dp tall (aspect 3:4
 * preview). The [content] slot receives a Box constrained to the preview area so callers
 * can paint an actual thumbnail — this component owns the frame, selection ring, and label
 * only.
 *
 * On dark chrome (Edit is dark by DESIGN §7.6), unselected label is CameraInkMuted; selected
 * is CameraInk on a 2dp Saffron ring.
 */
@Composable
fun NpicFilterPreview(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
    content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(NpicColors.SurfaceRaised))
    },
) {
    val chrome = LocalNpicChrome.current
    val ringColor by animateColorAsState(
        targetValue = if (selected) NpicColors.Saffron else Color.Transparent,
        animationSpec = NpicMotion.standard(),
        label = "filter_ring",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            selected && onDark  -> chrome.cameraInk
            !selected && onDark -> chrome.cameraInkMuted
            selected            -> NpicColors.Ink
            else                -> chrome.inkMuted
        },
        animationSpec = NpicMotion.standard(),
        label = "filter_label",
    )

    Column(
        modifier = modifier.width(88.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .width(88.dp)
                .aspectRatio(3f / 4f)
                .clip(NpicShapes.sm)
                .background(NpicColors.SurfaceRaised, NpicShapes.sm)
                .border(2.dp, ringColor, NpicShapes.sm),
        ) {
            content()
        }
        Text(
            text  = label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight(600) else FontWeight(500),
            ),
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "FilterPreview — strip")
@Composable
private fun FilterPreviewStrip() {
    NpicTheme {
        val chrome = LocalNpicChrome.current
        Box(Modifier.background(chrome.cameraBg).padding(NpicSpacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
                NpicFilterPreview("Auto",     selected = true,  onClick = {})
                NpicFilterPreview("Original", selected = false, onClick = {})
                NpicFilterPreview("School ID", selected = false, onClick = {})
            }
        }
    }
}
