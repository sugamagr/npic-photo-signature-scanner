package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Modal bottom sheet. Top corners are `NpicShapes.sheetTop` (28dp), container is
 * `SurfaceRaised`, scrim uses the theme's `scrim` (Overlay 60% black).
 *
 * A 40×4dp `BorderStrong` drag handle sits at the top-center. Callers own the header row
 * (title + close), body, and action row — this component owns only the chrome and layout
 * spine (padding + navigationBars inset).
 *
 * Used by:
 * - Export sheet (format cards + share button)
 * - Signature Prompt sheet (Camera vs Draw choice)
 * - Sort / filter presets when they need more than a chip row
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpicBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    // m2501: optional scrim override. Default keeps the standard 60%-black backdrop
    // shared across dialogs. Individual sheets (Export) can pass a lighter value
    // when the intent is "peek at the sheet without hiding the screen behind it."
    scrimColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val chrome = LocalNpicChrome.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        shape            = NpicShapes.sheetTop,
        containerColor   = NpicColors.SurfaceRaised,
        contentColor     = NpicColors.Ink,
        scrimColor       = scrimColor ?: chrome.overlay,
        dragHandle       = {
            Box(
                Modifier
                    .padding(top = NpicSpacing.sm, bottom = NpicSpacing.xs)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(NpicShapes.full)
                    .background(chrome.borderStrong),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = NpicSpacing.xl, vertical = NpicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NpicSpacing.md),
        ) {
            if (title != null) {
                Text(
                    text  = title,
                    color = NpicColors.Ink,
                    // DESIGN §7.4 / §7.8: sheet header uses titleLarge (Fraunces 20sp),
                    // not headlineMedium (24sp). Applies to Save, Signature-prompt, Export.
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

// NOTE: ModalBottomSheet can't be previewed inline — it's a dialog. Preview left intentionally
// blank; QA the sheet in the emulator during Export/Signature-prompt integration.
@Preview(name = "Sheet — chrome only (visual reference)", showBackground = true)
@Composable
private fun SheetChromePreview() {
    NpicTheme {
        val chrome = LocalNpicChrome.current
        Box(Modifier.background(NpicColors.Ivory)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NpicShapes.sheetTop)
                    .background(NpicColors.SurfaceRaised)
                    .padding(NpicSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(NpicShapes.full)
                        .background(chrome.borderStrong),
                )
                Text("Export", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Choose a format and share with the portal.",
                    color = chrome.inkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
