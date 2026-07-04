package com.npic.photoandsignscanner.features.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicBottomSheet
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.core.ui.NpicCard
import com.npic.photoandsignscanner.domain.model.ExportFormat

/**
 * Export Format Sheet (PRD §4.10 + DESIGN §7.8). Three radio-selectable format cards,
 * a missing-media preflight warning that expands to a list of affected records, and a
 * Cancel/Export button row.
 *
 * The sheet loads records via [ExportViewModel] (by ID, not by handing whole records
 * through the route) so late deletes just shrink the target set instead of crashing.
 * The Share Sheet fire happens in the caller via [onShare] — the ViewModel returns a
 * list of file paths, the caller passes them to
 * [com.npic.photoandsignscanner.data.export.FileShareLauncher].
 */
@Composable
fun ExportSheet(
    viewModel: ExportViewModel,
    onCancel: () -> Unit,
    onShare: (result: ExportResult, format: ExportFormat) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NpicBottomSheet(onDismiss = onCancel, title = "Export") {
        SheetSubtitle(recordCount = state.recordCount)
        Spacer(Modifier.height(NpicSpacing.md))

        FormatCards(
            selected = state.format,
            onSelect = viewModel::setFormat,
        )

        if (state.hasWarning) {
            Spacer(Modifier.height(NpicSpacing.md))
            MissingMediaWarning(
                skippedCount = state.skipped.size,
                expanded = state.warningExpanded,
                onToggle = viewModel::toggleWarningExpanded,
                skippedNames = state.skipped.map { it.displayName.ifBlank { "Serial ${it.serial}" } },
            )
        }

        Spacer(Modifier.height(NpicSpacing.lg))

        ExportActionRow(
            effectiveCount = state.effective.size,
            canExport = state.canExport,
            onCancel = onCancel,
            onExport = {
                viewModel.beginExport { result ->
                    onShare(result, state.format)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subtitle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SheetSubtitle(recordCount: Int) {
    val chrome = LocalNpicChrome.current
    val subtitle = when (recordCount) {
        0    -> "Loading…"
        1    -> "1 student"
        else -> "$recordCount students"
    }
    Text(
        text  = subtitle,
        color = chrome.inkMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Format cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormatCards(
    selected: ExportFormat,
    onSelect: (ExportFormat) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
    ) {
        ExportFormat.entries.forEach { format ->
            FormatCard(
                format = format,
                icon = iconFor(format),
                isSelected = format == selected,
                onClick = { onSelect(format) },
            )
        }
    }
}

@Composable
private fun FormatCard(
    format: ExportFormat,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    NpicCard(
        onClick = onClick,
        selected = isSelected,
        padding = PaddingValues(NpicSpacing.md),
        modifier = Modifier
            // DESIGN §7.8: 72dp min-height format card. defaultMinSize (not fixed height)
            // keeps the tile expandable if a future format subtitle wraps to two lines.
            .defaultMinSize(minHeight = 72.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = isSelected
                contentDescription = "${format.title}. ${format.subtitle}"
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(NpicShapes.md)
                    // DESIGN §7.8: solid SaffronSoft fill on the icon container. Earlier
                    // impl used .copy(alpha = 0.55f) which read washed-out against Ivory.
                    .background(NpicColors.SaffronSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NpicColors.Saffron,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text  = format.title,
                    color = NpicColors.Ink,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(600)),
                )
                Text(
                    text  = format.subtitle,
                    color = chrome.inkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            RadioMark(isSelected = isSelected)
        }
    }
}

@Composable
private fun RadioMark(isSelected: Boolean) {
    val chrome = LocalNpicChrome.current
    // NpicShapes.full is the design-system's canonical pill/circle token. Using it here
    // instead of foundation.shape.CircleShape keeps the RadioMark aligned with the rest
    // of the theme (Oracle m-8c2).
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(NpicShapes.full)
            .background(
                if (isSelected) NpicColors.Saffron else NpicColors.Surface,
                NpicShapes.full,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(NpicShapes.full)
                    .background(NpicColors.Ink),
            )
        } else {
            // Outlined ring when unselected — 2dp BorderStrong stroke via a ring layout.
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(NpicShapes.full)
                    .background(chrome.borderStrong),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(NpicShapes.full)
                        .background(NpicColors.Surface),
                )
            }
        }
    }
}

private fun iconFor(format: ExportFormat): ImageVector = when (format) {
    ExportFormat.Combined      -> Icons.Outlined.PictureAsPdf
    ExportFormat.PhotoOnly     -> Icons.Outlined.PhotoCamera
    ExportFormat.SignatureOnly -> Icons.Outlined.Draw
}

// ─────────────────────────────────────────────────────────────────────────────
// Missing-media warning
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MissingMediaWarning(
    skippedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    skippedNames: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NpicShapes.md)
            .background(NpicColors.TerracottaSoft)
            .padding(NpicSpacing.md)
            // Polite live region so TalkBack announces the missing-media warning when it
            // appears after a format switch, without interrupting mid-sentence readouts
            // (Oracle M-8c2-M2-QCC).
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = NpicColors.Terracotta,
                modifier = Modifier.size(20.dp),
            )
            Text(
                // DESIGN §7.8 copy locked. Singular vs plural handled explicitly so the
                // string never reads "1 items don't have" (grammar mismatch caught by
                // Oracle O4-2 blind sweep). "It'll" / "They'll" tense agreement follows
                // subject count.
                text  = if (skippedCount == 1) {
                    "1 item doesn't have a signature. It'll be skipped."
                } else {
                    "$skippedCount items don't have a signature. They'll be skipped."
                },
                color = NpicColors.Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        val toggleLabel = if (expanded) "Hide list" else "Show list"
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(NpicShapes.sm)
                .semantics {
                    role = Role.Button
                    contentDescription = if (expanded) "Hide skipped list" else "Show skipped list"
                }
                .clickable(onClick = onToggle)
                .padding(horizontal = NpicSpacing.xxs, vertical = NpicSpacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text  = toggleLabel,
                color = NpicColors.Terracotta,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
            )
        }
        val reduceMotion = LocalReduceMotion.current
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(NpicMotion.standardOrSnap(reduceMotion)) +
                expandVertically(NpicMotion.standardOrSnap(reduceMotion)),
            exit = fadeOut(NpicMotion.fastOrSnap(reduceMotion)) +
                shrinkVertically(NpicMotion.fastOrSnap(reduceMotion)),
        ) {
            SkippedList(names = skippedNames)
        }
    }
}

@Composable
private fun SkippedList(names: List<String>) {
    val chrome = LocalNpicChrome.current
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 140.dp)
            .verticalScroll(scroll)
            .padding(top = NpicSpacing.xs)
            // Newly-revealed content inside AnimatedVisibility doesn't inherit the
            // parent warning's live region — TalkBack was announcing "Show list" but
            // never the names. Local Polite region reads the roster on expand
            // (Oracle O4-3).
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
    ) {
        names.forEach { name ->
            Text(
                text  = "• $name",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExportActionRow(
    effectiveCount: Int,
    canExport: Boolean,
    onCancel: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NpicButton(
            label = "Cancel",
            onClick = onCancel,
            style = NpicButtonStyle.Ghost,
        )
        NpicButton(
            label = when {
                effectiveCount <= 0 -> "Nothing to export"
                effectiveCount == 1 -> "Export"
                else                -> "Export $effectiveCount items"
            },
            onClick = onExport,
            modifier = Modifier.weight(1f),
            enabled = canExport,
        )
    }
}
