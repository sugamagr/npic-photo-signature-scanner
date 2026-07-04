package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Standard top bar. 56dp tall (below the status-bar inset). Fraunces title, optional nav
 * icon on the left, up to two action icons on the right.
 *
 * Backgrounds:
 * - Ivory by default (Gallery, Detail, Save, Export).
 * - Pass `backgroundColor` for camera chrome (`CameraBg`) or dialog contexts.
 *
 * Applies `statusBars` window inset padding so callers don't need to.
 */
@Composable
fun NpicTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navIcon: ImageVector? = null,
    onNavClick: (() -> Unit)? = null,
    navContentDescription: String = "Back",
    action1Icon: ImageVector? = null,
    action1ContentDescription: String = "",
    onAction1Click: (() -> Unit)? = null,
    action2Icon: ImageVector? = null,
    action2ContentDescription: String = "",
    onAction2Click: (() -> Unit)? = null,
    backgroundColor: Color = NpicColors.Ivory,
    contentColor: Color = NpicColors.Ink,
    onDark: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = NpicSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            if (navIcon != null && onNavClick != null) {
                NpicIconButton(
                    icon = navIcon,
                    contentDescription = navContentDescription,
                    onClick = onNavClick,
                    style = if (onDark) NpicIconButtonStyle.OnDark else NpicIconButtonStyle.Plain,
                )
            } else {
                Box(Modifier.padding(start = NpicSpacing.md))
            }

            Text(
                text  = title,
                color = contentColor,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = NpicSpacing.xs),
            )

            if (action1Icon != null && onAction1Click != null) {
                NpicIconButton(
                    icon = action1Icon,
                    contentDescription = action1ContentDescription,
                    onClick = onAction1Click,
                    style = if (onDark) NpicIconButtonStyle.OnDark else NpicIconButtonStyle.Plain,
                )
            }
            if (action2Icon != null && onAction2Click != null) {
                NpicIconButton(
                    icon = action2Icon,
                    contentDescription = action2ContentDescription,
                    onClick = onAction2Click,
                    style = if (onDark) NpicIconButtonStyle.OnDark else NpicIconButtonStyle.Plain,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "TopBar — gallery")
@Composable
private fun TopBarPreviewGallery() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory)) {
            NpicTopBar(
                title = "NPIC Scanner",
                action1Icon = Icons.Outlined.MoreVert,
                action1ContentDescription = "Menu",
                onAction1Click = {},
            )
        }
    }
}

@Preview(name = "TopBar — edit with back + close")
@Composable
private fun TopBarPreviewEdit() {
    NpicTheme {
        val chrome = LocalNpicChrome.current
        Box(Modifier.background(chrome.cameraBg)) {
            NpicTopBar(
                title = "Edit",
                navIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                onNavClick = {},
                action1Icon = Icons.Outlined.Close,
                action1ContentDescription = "Discard",
                onAction1Click = {},
                backgroundColor = chrome.cameraBg,
                contentColor = chrome.cameraInk,
                onDark = true,
            )
        }
    }
}
