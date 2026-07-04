package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Ephemeral banner. Ink container at 92% alpha, `NpicShapes.md`, 48dp tall min, Ivory text.
 * Optional leading icon + optional trailing action label (e.g. "Undo").
 *
 * Rendered by a screen-level host; this component is purely presentational — no timer or
 * queue logic. The Gallery feature owns its own host.
 *
 * Three semantic tones map to icon + subtle accent:
 * - [NpicToastTone.Neutral]  — no accent line, Info icon (optional).
 * - [NpicToastTone.Success]  — Sage accent, Check icon.
 * - [NpicToastTone.Error]    — Terracotta accent, Info icon.
 */

enum class NpicToastTone { Neutral, Success, Error }

@Composable
fun NpicToast(
    message: String,
    modifier: Modifier = Modifier,
    tone: NpicToastTone = NpicToastTone.Neutral,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val chrome = LocalNpicChrome.current
    val accent = when (tone) {
        NpicToastTone.Neutral -> null
        NpicToastTone.Success -> chrome.sage
        NpicToastTone.Error   -> chrome.terracotta
    }
    val resolvedIcon = icon ?: when (tone) {
        NpicToastTone.Success -> Icons.Filled.CheckCircle
        NpicToastTone.Error   -> Icons.Outlined.Info
        NpicToastTone.Neutral -> null
    }

    Box(
        modifier = modifier
            .clip(NpicShapes.md)
            .background(NpicColors.Ink.copy(alpha = 0.92f), NpicShapes.md)
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            if (resolvedIcon != null) {
                Icon(
                    imageVector = resolvedIcon,
                    contentDescription = null,
                    tint = accent ?: NpicColors.Ivory,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text  = message,
                color = NpicColors.Ivory,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    text  = actionLabel,
                    color = accent ?: NpicColors.Saffron,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(start = NpicSpacing.sm)
                        .clip(NpicShapes.sm)
                        .clickable(onClick = onAction)
                        .padding(horizontal = NpicSpacing.xs, vertical = NpicSpacing.xxs),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Toasts — tones")
@Composable
private fun ToastPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.md)) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
            ) {
                NpicToast("3 records deleted", tone = NpicToastTone.Neutral, actionLabel = "Undo", onAction = {})
                NpicToast("Saved", tone = NpicToastTone.Success)
                NpicToast("Signature missing on 2 items", tone = NpicToastTone.Error)
            }
        }
    }
}
