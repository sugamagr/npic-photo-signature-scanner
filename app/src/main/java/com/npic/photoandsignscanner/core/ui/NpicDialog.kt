package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicElevation
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Small modal dialog. Used for confirmations (delete, discard edits) and short prompts
 * (rename, class change). Bigger flows (Save, Duplicate detection) get purpose-built
 * screens or sheets instead — dialogs are the smallest surface class.
 *
 * Container: SurfaceRaised, `NpicShapes.xl`, level-3 shadow. Max width 400dp so it stays
 * comfortable on tablets. Title Fraunces, body Inter.
 *
 * The [primaryAction] slot expects a Primary/Destructive [NpicButton]; [secondaryAction]
 * expects Secondary/Ghost. Callers own the actual button labels + styles.
 */
@Composable
fun NpicDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    bodyContent: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
    primaryAction: @Composable () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    val chrome = LocalNpicChrome.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress    = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = modifier
                .widthIn(min = 280.dp, max = 400.dp)
                .padding(NpicSpacing.xl)
                .shadow(NpicElevation.level3, NpicShapes.xl, clip = false)
                .clip(NpicShapes.xl)
                .background(NpicColors.SurfaceRaised, NpicShapes.xl)
                .padding(NpicSpacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.md)) {
                Text(
                    text  = title,
                    color = NpicColors.Ink,
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (body != null) {
                    Text(
                        text  = body,
                        color = chrome.inkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                bodyContent?.invoke()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
                ) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    secondaryAction?.invoke()
                    primaryAction()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Dialog — delete confirm")
@Composable
private fun DialogPreview() {
    NpicTheme {
        NpicDialog(
            onDismissRequest = {},
            title = "Delete 3 records?",
            body  = "This will remove the photos and signatures. Exported files are not affected.",
            secondaryAction = {
                NpicButton("Cancel", onClick = {}, style = NpicButtonStyle.Secondary, size = NpicButtonSize.Small)
            },
            primaryAction = {
                NpicButton("Delete", onClick = {}, style = NpicButtonStyle.Destructive, size = NpicButtonSize.Small)
            },
        )
    }
}
