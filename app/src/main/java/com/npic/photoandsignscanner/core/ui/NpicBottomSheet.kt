package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    // m2498: default is `skipPartiallyExpanded = false` so the sheet opens at ~half
    // height and only expands when the user drags it up. The earlier m2497 fix wrapped
    // the sheet content in `verticalScroll` — that's what makes half-height safe: if
    // content overflows the partial viewport, it scrolls inside the sheet instead of
    // clipping the action row. Callers that need a full-height sheet from the start
    // (e.g. a canvas-taking editor) can still pass their own SheetState.
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    title: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val chrome = LocalNpicChrome.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        shape            = NpicShapes.sheetTop,
        containerColor   = NpicColors.SurfaceRaised,
        contentColor     = NpicColors.Ink,
        scrimColor       = chrome.overlay,
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
        // m2497: verticalScroll wraps content so overflow scrolls WITHIN the sheet
        // rather than clipping. Without this, adding sections to a sheet (like the
        // m2496 naming toggle in ExportSheet) can push the action row below the
        // visible viewport — users see only the primary button, the Ghost buttons
        // silently disappear, and there's no scroll gesture to recover them. This
        // was the m2497 "Save to Gallery and Save & Share not visible at all" bug.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
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
