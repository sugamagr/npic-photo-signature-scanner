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
import com.npic.photoandsignscanner.domain.model.NamingMode

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

        // m2496: naming-mode toggle. Only rendered when at least one selected record
        // was originally saved under Name mode — pure-Serial batches export as
        // `090001.jpeg` regardless, so the toggle would be a dead switch.
        if (state.showNamingToggle) {
            Spacer(Modifier.height(NpicSpacing.md))
            NamingToggleSection(
                override = state.namingOverride,
                onSelect = viewModel::setNamingOverride,
            )
        }

        if (state.hasWarning) {
            Spacer(Modifier.height(NpicSpacing.md))
            MissingMediaWarning(
                skippedCount = state.skipped.size,
                format = state.format,
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
            onSaveToGallery = {
                viewModel.beginSaveToGallery { result -> onShare(result, state.format) }
            },
            onShare = {
                viewModel.beginExport { result -> onShare(result, state.format) }
            },
            onSaveAndShare = {
                viewModel.beginSaveAndShare { result -> onShare(result, state.format) }
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
    format: com.npic.photoandsignscanner.domain.model.ExportFormat,
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
                // qc-round-13 MINOR: message noun switches on format so PhotoOnly reads
                // "photo" not "signature". Combined + SignatureOnly still say signature.
                // Singular vs plural + "It'll" / "They'll" tense agreement follows count.
                text  = missingMediaMessage(skippedCount, format),
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
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onSaveAndShare: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
    ) {
        // m2475: Share is the primary action per user directive — teachers upload the
        // JPEG to the portal 100% of the time; Save-to-Gallery is a rare "keep a copy"
        // sink. Big filled Share button on top; Save & Share + Save-to-Gallery drop
        // to the Ghost row below.
        val primaryLabel = when {
            effectiveCount <= 0 -> "Nothing to export"
            effectiveCount == 1 -> "Share"
            else                -> "Share $effectiveCount"
        }
        NpicButton(
            label = primaryLabel,
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            enabled = canExport,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NpicButton(
                label = "Save to Gallery",
                onClick = onSaveToGallery,
                style = NpicButtonStyle.Ghost,
                modifier = Modifier.weight(1f),
                enabled = canExport,
            )
            NpicButton(
                label = "Save & Share",
                onClick = onSaveAndShare,
                style = NpicButtonStyle.Ghost,
                modifier = Modifier.weight(1f),
                enabled = canExport,
            )
        }
        NpicButton(
            label = "Cancel",
            onClick = onCancel,
            style = NpicButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun missingMediaMessage(
    count: Int,
    format: com.npic.photoandsignscanner.domain.model.ExportFormat,
): String {
    val missing = when (format) {
        com.npic.photoandsignscanner.domain.model.ExportFormat.PhotoOnly -> "photo"
        com.npic.photoandsignscanner.domain.model.ExportFormat.SignatureOnly,
        com.npic.photoandsignscanner.domain.model.ExportFormat.Combined -> "signature"
    }
    return if (count == 1) {
        "1 item doesn't have a $missing. It'll be skipped."
    } else {
        "$count items don't have a $missing. They'll be skipped."
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Naming-mode toggle (m2496)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Filename naming-mode picker. Visible in the sheet only when at least one selected
 * record was originally saved under Name mode (see [ExportUiState.showNamingToggle]).
 *
 * m2498: two side-by-side chip pills — "By name" and "By serial" — one selected, the
 * other outlined. Replaces the m2497 single morphing chip (which gave no affordance
 * for what tapping would do) and the pre-m2497 two-tab NpicSegmentedControl (which
 * pushed the sheet's intrinsic height past the half-expanded state).
 *
 * Default is Name (per user directive). Serial-mode records always ignore the
 * override — their filename is deterministically `{portalCode}{serial:04d}.jpeg`.
 */
@Composable
private fun NamingToggleSection(
    override: NamingMode.Kind?,
    onSelect: (NamingMode.Kind) -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val selected = override ?: NamingMode.Kind.Name
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = "Filename",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(NpicSpacing.xxs))
        Text(
            text  = "For records saved with a name. Serial-only records always export as their portal number.",
            color = chrome.inkMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(NpicSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs)) {
            com.npic.photoandsignscanner.core.ui.NpicChip(
                label    = "By name",
                selected = selected == NamingMode.Kind.Name,
                onClick  = { onSelect(NamingMode.Kind.Name) },
            )
            com.npic.photoandsignscanner.core.ui.NpicChip(
                label    = "By serial",
                selected = selected == NamingMode.Kind.Serial,
                onClick  = { onSelect(NamingMode.Kind.Serial) },
            )
        }
    }
}
