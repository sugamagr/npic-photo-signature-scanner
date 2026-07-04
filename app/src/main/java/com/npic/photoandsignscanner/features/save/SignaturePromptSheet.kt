package com.npic.photoandsignscanner.features.save

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicBottomSheet

/**
 * Signature Prompt (PRD §3 golden path, §4.3). Shown after Edit completes for the photo
 * — asks the user to attach a signature via Camera capture, Draw canvas, or Skip
 * straight to Save.
 *
 * Three equal-weight rows (matching DESIGN §7.8 Export sheet card pattern): icon + label
 * + subtitle stacked vertically inside a 72dp NpicCard-like tile, radio-style tap.
 */
@Composable
fun SignaturePromptSheet(
    onCapture: () -> Unit,
    onDraw: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    NpicBottomSheet(
        onDismiss = onDismiss,
        title = "Add a signature?",
    ) {
        val chrome = LocalNpicChrome.current
        Text(
            text  = "You can also skip and save with just the photo.",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        PromptRow(
            icon = Icons.Outlined.CameraAlt,
            title = "Capture",
            subtitle = "Photograph the signature on the form",
            onClick = onCapture,
        )
        PromptRow(
            icon = Icons.Outlined.Draw,
            title = "Draw",
            subtitle = "Draw the signature on-screen",
            onClick = onDraw,
        )
        PromptRow(
            icon = Icons.Outlined.SkipNext,
            title = "Skip",
            subtitle = "Save just the photo for now",
            onClick = onSkip,
        )
    }
}

@Composable
private fun PromptRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NpicShapes.md)
            .background(NpicColors.Surface, NpicShapes.md)
            .border(1.dp, chrome.borderSoft, NpicShapes.md)
            .clickable(onClick = onClick)
            .padding(NpicSpacing.md)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(NpicShapes.sm)
                .background(chrome.saffronSoft.copy(alpha = 0.35f), NpicShapes.sm),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NpicColors.Saffron,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(600)),
            )
            Text(
                text  = subtitle,
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
