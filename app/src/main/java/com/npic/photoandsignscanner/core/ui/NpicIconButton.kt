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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Icon-only tap target. Minimum 44dp square hit area (DESIGN §11.7 — WCAG 2.5.5 target size).
 * Used in top bars, tool tabs, gallery selection mode, and inline row actions.
 *
 * Two style tracks:
 * - [NpicIconButtonStyle.Plain]     — tint only, no container. Default for top bars.
 * - [NpicIconButtonStyle.Filled]    — Saffron container + Ink tint. Camera shutter, primary FAB
 *                                     accessories.
 * - [NpicIconButtonStyle.Outline]   — 1dp BorderStrong ring. Signature clear/undo/redo.
 * - [NpicIconButtonStyle.OnDark]    — For dark chrome; tints CameraInk, transparent container.
 */

enum class NpicIconButtonStyle { Plain, Filled, Outline, OnDark }

@Composable
fun NpicIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NpicIconButtonStyle = NpicIconButtonStyle.Plain,
    enabled: Boolean = true,
    sizeDp: Int = 44,
    iconSizeDp: Int = 24,
) {
    val chrome = LocalNpicChrome.current
    val (container, tint, border) = when (style) {
        NpicIconButtonStyle.Plain -> Triple(
            Color.Transparent,
            if (enabled) NpicColors.Ink else chrome.inkFaint,
            null,
        )
        NpicIconButtonStyle.Filled -> Triple(
            if (enabled) NpicColors.Saffron else chrome.borderStrong,
            if (enabled) NpicColors.Ink else chrome.inkFaint,
            null,
        )
        NpicIconButtonStyle.Outline -> Triple(
            Color.Transparent,
            if (enabled) NpicColors.Ink else chrome.inkFaint,
            if (enabled) chrome.borderStrong else chrome.borderSoft,
        )
        NpicIconButtonStyle.OnDark -> Triple(
            Color.Transparent,
            if (enabled) chrome.cameraInk else chrome.cameraInkMuted,
            null,
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = com.npic.photoandsignscanner.core.theme.LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = com.npic.photoandsignscanner.core.theme.NpicMotion.springSnappyOrSnap(reduceMotion),
        label = "npicIconButton_pressScale",
    )

    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(sizeDp.dp)
            .clip(NpicShapes.full)
            .background(container, NpicShapes.full)
            .let { m ->
                border?.let { m.border(width = 1.dp, color = it, shape = NpicShapes.full) } ?: m
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = (sizeDp / 2).dp, color = tint),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // handled at Box level for a11y
            tint = tint,
            modifier = Modifier.size(iconSizeDp.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "IconButtons — all styles")
@Composable
private fun IconButtonPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).size(320.dp, 96.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NpicIconButton(Icons.Outlined.Share, "Share", {}, style = NpicIconButtonStyle.Plain)
                NpicIconButton(Icons.Outlined.Share, "Share", {}, style = NpicIconButtonStyle.Filled)
                NpicIconButton(Icons.Outlined.Delete, "Delete", {}, style = NpicIconButtonStyle.Outline)
                NpicIconButton(Icons.Outlined.Share, "Disabled", {}, enabled = false)
            }
        }
    }
}
