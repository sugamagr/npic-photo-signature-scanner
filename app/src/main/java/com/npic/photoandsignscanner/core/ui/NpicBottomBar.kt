package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Bottom action bar. Used when a screen has a persistent primary + secondary action row
 * (Save dialog, Export sheet review row, Gallery selection action bar). Applies
 * `navigationBars` window inset padding.
 *
 * The [content] slot lets the caller drop in any composition of [NpicButton]s or icon
 * groups. No opinionated layout — just the visual chrome (72dp min, top hairline border,
 * container).
 */
@Composable
fun NpicBottomBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NpicColors.Surface,
    showTopBorder: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .let { m ->
                if (showTopBorder) m.border(
                    width = 1.dp, color = chrome.borderSoft,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                ) else m
            }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = NpicSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
            content = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "BottomBar — selection actions")
@Composable
private fun BottomBarPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory)) {
            NpicBottomBar {
                NpicButton(
                    label = "Export 3",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    startIcon = Icons.Outlined.Share,
                )
                NpicButton(
                    label = "Delete",
                    onClick = {},
                    style = NpicButtonStyle.Destructive,
                    startIcon = Icons.Outlined.Delete,
                )
            }
        }
    }
}
