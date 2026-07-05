package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * The single button component for the whole app.
 *
 * All four visual variants live behind one API. Callers pick a [NpicButtonStyle] and a
 * [NpicButtonSize]; the component owns colors, shapes, spacing, disabled state, pressed
 * scale, loading state, and icon slots. Feature code MUST NOT hand-roll a button.
 *
 * Sizing (DESIGN §7.2):
 * - Large  52dp tall — primary CTAs on Save dialog, Export sheet, Camera continuation
 * - Small  44dp tall — secondary rows, dialog dismiss buttons, filter reset
 *
 * Styles (DESIGN §7.2):
 * - Primary      Saffron fill,       Ink label,          Level 3 press elevation
 * - Secondary    transparent fill,   Ink label,          1dp BorderStrong border
 * - Destructive  Terracotta fill,    Ivory label,        for irreversible actions only
 * - Ghost        transparent fill,   Saffron label,      no border; inline links
 *
 * A `startIcon` slot renders a 20dp icon before the label with 8dp gap.
 * `loading = true` disables the button and shows a 20dp indeterminate spinner in the label
 * position — used during export encoding and other async work.
 */

enum class NpicButtonStyle { Primary, Secondary, Destructive, Ghost }

enum class NpicButtonSize(val heightDp: Int, val horizontalPaddingDp: Int) {
    Large(heightDp = 52, horizontalPaddingDp = 20),
    Small(heightDp = 44, horizontalPaddingDp = 20),
}

@Immutable
private data class ButtonColors(
    val container: Color,
    val label:     Color,
    val border:    Color?,
    val gradient:  Brush? = null,
)

@Composable
private fun resolveColors(style: NpicButtonStyle, enabled: Boolean): ButtonColors {
    val chrome = LocalNpicChrome.current
    return when (style) {
        NpicButtonStyle.Primary -> ButtonColors(
            container = if (enabled) NpicColors.Saffron else chrome.borderStrong,
            label     = if (enabled) NpicColors.Ink     else chrome.inkFaint,
            border    = null,
            gradient  = if (enabled) {
                Brush.verticalGradient(listOf(NpicColors.SaffronBright, NpicColors.Saffron))
            } else null,
        )
        NpicButtonStyle.Secondary -> ButtonColors(
            container = Color.Transparent,
            label     = if (enabled) NpicColors.Ink     else chrome.inkFaint,
            border    = if (enabled) chrome.borderStrong else chrome.borderSoft,
        )
        NpicButtonStyle.Destructive -> ButtonColors(
            container = if (enabled) chrome.terracotta else chrome.borderStrong,
            label     = if (enabled) NpicColors.Ivory   else chrome.inkFaint,
            border    = null,
        )
        NpicButtonStyle.Ghost -> ButtonColors(
            container = Color.Transparent,
            label     = if (enabled) NpicColors.Saffron else chrome.inkFaint,
            border    = null,
        )
    }
}

@Composable
fun NpicButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NpicButtonStyle = NpicButtonStyle.Primary,
    size:  NpicButtonSize  = NpicButtonSize.Large,
    enabled: Boolean = true,
    loading: Boolean = false,
    startIcon: ImageVector? = null,
    shape: CornerBasedShape = NpicShapes.md,
) {
    val colors = resolveColors(style, enabled && !loading)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !loading) 0.96f else 1f,
        animationSpec = NpicMotion.springSnappyOrSnap(reduceMotion),
        label = "npicButton_pressScale",
    )

    val base = modifier
        .semantics { role = Role.Button }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .height(size.heightDp.dp)
        .defaultMinSize(minWidth = 88.dp)
        .clip(shape)
        .let { m ->
            colors.gradient
                ?.let { g -> m.background(g, shape) }
                ?: m.background(colors.container, shape)
        }
        .let { m ->
            colors.border?.let { m.border(width = 1.dp, color = it, shape = shape) } ?: m
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = colors.label),
            enabled = enabled && !loading,
            onClick = onClick,
        )
        .padding(horizontal = size.horizontalPaddingDp.dp)

    Box(modifier = base, contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.label,
                        strokeWidth = 2.dp,
                    )
                }
                startIcon != null -> {
                    Icon(
                        imageVector = startIcon,
                        contentDescription = null,
                        tint = colors.label,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text  = label,
                        color = colors.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
                    )
                }
                else -> {
                    Text(
                        text  = label,
                        color = colors.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(600)),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Buttons — all styles, large")
@Composable
private fun ButtonPreviewLarge() {
    NpicTheme {
        Box(Modifier.padding(NpicSpacing.md).background(NpicColors.Ivory)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md)) {
                NpicButton("Save",     onClick = {}, style = NpicButtonStyle.Primary)
                NpicButton("Cancel",   onClick = {}, style = NpicButtonStyle.Secondary)
                NpicButton("Delete",   onClick = {}, style = NpicButtonStyle.Destructive)
                NpicButton("Learn more", onClick = {}, style = NpicButtonStyle.Ghost)
            }
        }
    }
}

@Preview(name = "Buttons — small + states")
@Composable
private fun ButtonPreviewSmall() {
    NpicTheme {
        Box(Modifier.padding(NpicSpacing.md).background(NpicColors.Ivory)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md)) {
                NpicButton("Retake", onClick = {}, style = NpicButtonStyle.Primary, size = NpicButtonSize.Small)
                NpicButton("Disabled", onClick = {}, style = NpicButtonStyle.Primary, size = NpicButtonSize.Small, enabled = false)
                NpicButton("Exporting…", onClick = {}, style = NpicButtonStyle.Primary, size = NpicButtonSize.Small, loading = true)
            }
        }
    }
}
