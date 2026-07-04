package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicElevation
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Content island. Sits on Ivory background and lifts content by container swap + optional
 * shadow. DESIGN §4.3: NEVER both border and shadow on the same element — pick one.
 *
 * Two flavors:
 * - [NpicCardStyle.Flat]     — Surface fill, 1dp BorderSoft. Default for list rows and
 *                              settings groups.
 * - [NpicCardStyle.Raised]   — SurfaceRaised fill + level-1 shadow. For export-format cards,
 *                              duplicate-preview cards, home cards.
 */

enum class NpicCardStyle { Flat, Raised }

@Composable
fun NpicCard(
    modifier: Modifier = Modifier,
    style: NpicCardStyle = NpicCardStyle.Flat,
    shape: CornerBasedShape = NpicShapes.lg,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    padding: PaddingValues = PaddingValues(NpicSpacing.md),
    elevation: Dp = if (style == NpicCardStyle.Raised) NpicElevation.level1 else NpicElevation.level0,
    content: @Composable () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val container = when (style) {
        NpicCardStyle.Flat   -> NpicColors.Surface
        NpicCardStyle.Raised -> NpicColors.SurfaceRaised
    }
    val borderColor: Color? = when {
        selected              -> NpicColors.Saffron
        style == NpicCardStyle.Flat -> chrome.borderSoft
        else                  -> null
    }
    val borderWidth = if (selected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { m -> if (elevation > 0.dp) m.shadow(elevation, shape, clip = false) else m }
            .clip(shape)
            .background(container, shape)
            .let { m -> borderColor?.let { m.border(borderWidth, it, shape) } ?: m }
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .padding(padding),
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Cards — flat and raised")
@Composable
private fun CardPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.md)) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NpicSpacing.sm),
            ) {
                NpicCard(style = NpicCardStyle.Flat) { Text("Flat card, list row") }
                NpicCard(style = NpicCardStyle.Raised) { Text("Raised card, export format") }
                NpicCard(style = NpicCardStyle.Raised, selected = true) { Text("Selected") }
            }
        }
    }
}
