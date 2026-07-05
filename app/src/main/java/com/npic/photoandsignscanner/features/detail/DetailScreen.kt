package com.npic.photoandsignscanner.features.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.core.ui.NpicCard
import com.npic.photoandsignscanner.core.ui.NpicIconButton
import com.npic.photoandsignscanner.core.ui.NpicMenu
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.StudentRecord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock

/**
 * Detail screen (PRD §4.9 + DESIGN §7.7). Renders one student record with metadata,
 * photo card, signature card, and an Export bottom bar. Overflow menu covers Edit,
 * Delete, and Duplicate-to-another-class.
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onEditPhoto: (StudentRecord) -> Unit,
    onEditSignature: (StudentRecord) -> Unit,
    // m2232: All add-media callbacks now forward the current record so MainActivity
    // routes through captureHolder.beginUpdate(record) instead of clear() + fresh
    // draft. Preserves record identity across the Camera / Draw / Import flow so
    // signatures re-attach to the existing StudentRecord via repo.replace() rather
    // than surfacing as phantom rows in the Save sheet.
    onCapturePhoto: (StudentRecord) -> Unit,
    onImportPhoto: (StudentRecord) -> Unit,
    onCaptureSignature: (StudentRecord) -> Unit,
    onDrawSignature: (StudentRecord) -> Unit,
    onImportSignature: (StudentRecord) -> Unit,
    onExport: (StudentRecord) -> Unit,
    onDuplicateToAnotherClass: (record: StudentRecord, target: ClassNum) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDuplicatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.notFound) {
        if (state.notFound) currentOnBack()
    }

    Column(Modifier.fillMaxSize().background(NpicColors.Ivory)) {
        DetailTopBar(
            title = state.record?.headerTitle ?: "Loading…",
            onBack = onBack,
            onEdit = { state.record?.let { onEditPhoto(it) } },
            onDelete = { showDeleteConfirm = true },
            onDuplicateToClass = { if (state.record != null) showDuplicatePicker = true },
            menuEnabled = state.record != null,
        )

        // Match Gallery's destructive-action pattern with an explicit confirm dialog.
        // Detail didn't have one before — Oracle m-8c1 UX inconsistency fix.
        if (showDeleteConfirm) {
            DeleteConfirmDialog(
                onKeep  = { showDeleteConfirm = false },
                onDelete = {
                    showDeleteConfirm = false
                    viewModel.delete(onDeleted)
                },
            )
        }

        val activeRecord = state.record
        if (showDuplicatePicker && activeRecord != null) {
            TargetClassPickerDialog(
                sourceClass = activeRecord.classNum,
                onDismiss = { showDuplicatePicker = false },
                onPick = { target ->
                    showDuplicatePicker = false
                    onDuplicateToAnotherClass(activeRecord, target)
                },
            )
        }

        val record = state.record
        if (record == null) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = NpicColors.Saffron)
                }
            }
        } else {
            DetailBody(
                record = record,
                modifier = Modifier.weight(1f),
                onEditPhoto = onEditPhoto,
                onEditSignature = onEditSignature,
                onCapturePhoto = onCapturePhoto,
                onImportPhoto = onImportPhoto,
                onCaptureSignature = onCaptureSignature,
                onDrawSignature = onDrawSignature,
                onImportSignature = onImportSignature,
                onDuplicateToAnotherClass = { showDuplicatePicker = true },
            )
            DetailExportBar(
                record = record,
                onExport = onExport,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar with overflow menu
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicateToClass: () -> Unit,
    menuEnabled: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().background(NpicColors.Ivory)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = NpicSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NpicIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Text(
                text  = title,
                color = NpicColors.Ink,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = NpicSpacing.xs),
            )
            Box {
                NpicIconButton(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    onClick = { if (menuEnabled) menuOpen = true },
                )
                NpicMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    item(
                        label = "Edit",
                        icon  = Icons.Outlined.Edit,
                        onClick = onEdit,
                    )
                    item(
                        label = "Duplicate to another class",
                        icon  = Icons.Outlined.ContentCopy,
                        onClick = onDuplicateToClass,
                    )
                    divider()
                    item(
                        label = "Delete",
                        icon  = Icons.Outlined.Delete,
                        destructive = true,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Body (metadata + photo card + signature card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailBody(
    record: StudentRecord,
    modifier: Modifier,
    onEditPhoto: (StudentRecord) -> Unit,
    onEditSignature: (StudentRecord) -> Unit,
    onCapturePhoto: (StudentRecord) -> Unit,
    onImportPhoto: (StudentRecord) -> Unit,
    onCaptureSignature: (StudentRecord) -> Unit,
    onDrawSignature: (StudentRecord) -> Unit,
    onImportSignature: (StudentRecord) -> Unit,
    onDuplicateToAnotherClass: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start  = NpicSpacing.md,
                end    = NpicSpacing.md,
                top    = NpicSpacing.md,
                // Oracle O5-m-11: extra bottom breathing room so the last card
                // isn't visually clipped by the DetailExportBar sibling.
                bottom = NpicSpacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.lg),
    ) {
        MetadataCard(record)
        PhotoCard(
            record = record,
            onEdit = { onEditPhoto(record) },
            onCapture = { onCapturePhoto(record) },
            onImport = { onImportPhoto(record) },
        )
        SignatureCard(
            record = record,
            onEdit = { onEditSignature(record) },
            onCapture = { onCaptureSignature(record) },
            onDraw = { onDrawSignature(record) },
            onImport = { onImportSignature(record) },
        )
        // User m1555: Duplicate-to-another-class entry point in the Detail body, in
        // addition to the overflow menu item. Both routes open the same target-class
        // picker → runs the same copy + Save flow.
        NpicButton(
            label     = "Duplicate to another class",
            onClick   = onDuplicateToAnotherClass,
            modifier  = Modifier.fillMaxWidth(),
            style     = NpicButtonStyle.Ghost,
            startIcon = Icons.Outlined.ContentCopy,
        )
    }
}

@Composable
private fun MetadataCard(record: StudentRecord) {
    // Cache the relative-time label. `record.createdAtLabel` calls Clock.System.now()
    // on every access; without remember, every recomposition of MetadataCard fires
    // the wall-clock read + Duration bucket lookup — cheap individually but noisy at
    // scale (Detail recomposes on any state change under it).
    val createdAtLabel = remember(record.createdAt) { record.createdAtLabel }
    NpicCard(
        padding = PaddingValues(NpicSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.lg)) {
                MetadataCell("Class", "Class ${record.classNum.label}", Modifier.weight(1f))
                MetadataCell("Serial", record.serialLabel, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.lg)) {
                MetadataCell("Captured", createdAtLabel, Modifier.weight(1f))
                MetadataCell(
                    key   = "Media",
                    value = record.mediaSummary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetadataCell(key: String, value: String, modifier: Modifier = Modifier) {
    val chrome = LocalNpicChrome.current
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$key: $value"
        },
    ) {
        Text(
            text  = key,
            color = chrome.inkMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(NpicSpacing.xxs))
        Text(
            text  = value,
            color = NpicColors.Ink,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PhotoCard(
    record: StudentRecord,
    onEdit: () -> Unit,
    onCapture: () -> Unit,
    onImport: () -> Unit,
) {
    val hasPhoto = record.photoPath.isNotBlank()
    NpicCard(
        style   = com.npic.photoandsignscanner.core.ui.NpicCardStyle.Flat,
        onClick = if (hasPhoto) onEdit else null,
        padding = PaddingValues(NpicSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
            SectionHeader(
                title = "Photo",
                editPill = if (hasPhoto) SectionHeaderPill(onClick = onEdit) else null,
            )
            if (hasPhoto) {
                PhotoPlaceholder(path = record.photoPath)
            } else {
                // m2403 Bug T: taller photo card (0.72 ≈ passport 35×45mm ratio) —
                // was 0.8 (~1.25× width tall), now ~1.39× width tall. Paired with
                // shorter signature strip 3.6f below.
                DashedPlaceholder(
                    aspectRatio = 0.72f,
                    label = "No photo yet",
                    icon = Icons.Outlined.CameraAlt,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
                ) {
                    // m2056 Item 3 fix: icon-above-label tiles fit 3 buttons across a
                    // 360dp phone without label wrapping ("Cap ture") — see IconTextTile.
                    IconTextTile(
                        icon     = Icons.Outlined.CameraAlt,
                        label    = "Capture",
                        onClick  = onCapture,
                        modifier = Modifier.weight(1f),
                    )
                    IconTextTile(
                        icon     = Icons.Outlined.PhotoLibrary,
                        label    = "Import",
                        onClick  = onImport,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureCard(
    record: StudentRecord,
    onEdit: () -> Unit,
    onCapture: () -> Unit,
    onDraw: () -> Unit,
    onImport: () -> Unit,
) {
    val hasSig = record.hasSignature
    NpicCard(
        onClick = if (hasSig) onEdit else null,
        padding = PaddingValues(NpicSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
            SectionHeader(
                title = "Signature",
                editPill = if (hasSig) SectionHeaderPill(onClick = onEdit) else null,
            )
            if (hasSig) {
                SignaturePlaceholder(path = record.signaturePath.orEmpty())
            } else {
                // m2403 Bug T: shorter signature strip (3.6f — 0.28× width tall)
                // was 3f (0.33× width). Paired with taller photo card 0.72 above.
                DashedPlaceholder(
                    aspectRatio = 3.6f,
                    label = "No signature yet",
                    icon = Icons.Outlined.Draw,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
                ) {
                    IconTextTile(
                        icon     = Icons.Outlined.CameraAlt,
                        label    = "Capture",
                        onClick  = onCapture,
                        modifier = Modifier.weight(1f),
                    )
                    IconTextTile(
                        icon     = Icons.Outlined.Draw,
                        label    = "Draw",
                        onClick  = onDraw,
                        modifier = Modifier.weight(1f),
                    )
                    IconTextTile(
                        icon     = Icons.Outlined.PhotoLibrary,
                        label    = "Import",
                        onClick  = onImport,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Present-photo card image (m2403 Bug T: 0.72 aspect ≈ passport 35×45mm — was 0.8/DESIGN
 * §7.7 8:10). Bug#1+#2: loads the SourceStore asset via Coil. Falls back to a
 * SaffronSoft-tinted placeholder if the file is missing.
 */
@Composable
private fun PhotoPlaceholder(path: String) {
    // m2403 Bug T: matches DashedPlaceholder's 0.72 so filled + unfilled cards stay
    // the same height.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(NpicShapes.md)
            .background(NpicColors.SaffronSoft.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (path.isNotBlank()) {
            coil.compose.AsyncImage(
                model = java.io.File(path),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = NpicColors.Saffron,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/**
 * Present-signature card image (3:1 aspect per DESIGN §7.7). Bug#1+#2: loads the
 * SourceStore asset via Coil with the same fallback behavior as [PhotoPlaceholder].
 */
@Composable
private fun SignaturePlaceholder(path: String) {
    // m2403 Bug T: matches DashedPlaceholder's 3.6 so filled + unfilled cards stay
    // the same height.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3.6f)
            .clip(NpicShapes.md)
            .background(NpicColors.SaffronSoft.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (path.isNotBlank()) {
            coil.compose.AsyncImage(
                model = java.io.File(path),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Draw,
                contentDescription = null,
                tint = NpicColors.Saffron,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun DashedPlaceholder(
    aspectRatio: Float,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val chrome = LocalNpicChrome.current
    // Hoisted so PathEffect isn't allocated on every Canvas draw pass.
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val cornerPx = 14.dp.toPx()
            // Oracle O5-m-10: drawRoundRect is already a DrawScope method;
            // wrapping in drawIntoCanvas allocated a Canvas wrapper per frame
            // for no reason. Call it directly.
            drawRoundRect(
                color        = chrome.borderStrong,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style        = Stroke(
                    width      = 1.5.dp.toPx(),
                    pathEffect = dashEffect,
                ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(NpicSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NpicColors.SaffronSoft.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NpicColors.Saffron,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(NpicSpacing.sm))
            Text(
                text  = label,
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Export bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailExportBar(
    record: StudentRecord,
    onExport: (StudentRecord) -> Unit,
) {
    val chrome = LocalNpicChrome.current
    // m2475: Detail Export button label stays a plain "Export". The Export sheet
    // itself surfaces the missing-media warning (MissingMediaWarning) with the exact
    // count + polite live region, so redundant "(needs both media)" hint on the
    // button was noise. canExportCombined still gates the disabled state.
    val canExportCombined = record.photoPath.isNotBlank() && record.hasSignature
    val label = "Export"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NpicColors.Surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(chrome.borderSoft),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
            ) {
                NpicButton(
                    label   = label,
                    onClick = { onExport(record) },
                    modifier = Modifier.fillMaxWidth(),
                    // Even without both media the Photo-only and Signature-only formats
                    // remain valid — the format sheet handles disabling Combined.
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Record-derived display helpers (kept private to this file — they're only used here)
// ─────────────────────────────────────────────────────────────────────────────

private val StudentRecord.headerTitle: String
    get() = if (displayName.isNotBlank()) displayName else serialLabel

private val StudentRecord.serialLabel: String
    get() = "${classNum.portalCode}${serial.toString().padStart(4, '0')}"

private val StudentRecord.mediaSummary: String
    get() = when {
        photoPath.isNotBlank() && hasSignature -> "Photo + signature"
        photoPath.isNotBlank()                 -> "Photo only"
        hasSignature                           -> "Signature only"
        else                                   -> "Not saved"
    }

private val StudentRecord.createdAtLabel: String
    get() = relativeTimeLabel(Clock.System.now() - createdAt)

private fun relativeTimeLabel(delta: Duration): String = when {
    delta < 1.minutes  -> "Just now"
    delta < 1.hours    -> "${delta.inWholeMinutes}m ago"
    delta < 24.hours   -> "${delta.inWholeHours}h ago"
    delta < 2.days     -> "Yesterday"
    delta < 30.days    -> "${delta.inWholeDays}d ago"
    else               -> "${(delta.inWholeDays / 30)} mo ago"
}

@Composable
private fun DeleteConfirmDialog(
    onKeep: () -> Unit,
    onDelete: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Delete this record?", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Text(
                "The photo and signature will be removed. This can't be undone.",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            NpicButton(
                label   = "Delete",
                onClick = onDelete,
                style   = NpicButtonStyle.Destructive,
            )
        },
        dismissButton = {
            NpicButton(
                label   = "Keep",
                onClick = onKeep,
                style   = NpicButtonStyle.Ghost,
            )
        },
    )
}

@Composable
private fun TargetClassPickerDialog(
    sourceClass: ClassNum,
    onDismiss: () -> Unit,
    onPick: (ClassNum) -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val targets = ClassNum.entries.filter { it != sourceClass }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Duplicate to which class?",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
                Text(
                    "The photo and signature will be copied. You'll enter a new serial or name on the next screen.",
                    color = chrome.inkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(NpicSpacing.xs))
                targets.forEach { target ->
                    NpicButton(
                        label   = "Class ${target.label}",
                        onClick = { onPick(target) },
                        modifier = Modifier.fillMaxWidth(),
                        style   = NpicButtonStyle.Secondary,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            NpicButton(
                label   = "Cancel",
                onClick = onDismiss,
                style   = NpicButtonStyle.Ghost,
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header + edit pill (m2056 Item 4)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Photo / Signature card header. Symmetric across both media types. When [editPill] is
 * non-null the header renders a compact Saffron "Edit" affordance in the trailing edge
 * so the edit action stays discoverable without needing the passive "Tap to edit" hint
 * that used to sit under the media.
 */
@Immutable
private data class SectionHeaderPill(val onClick: () -> Unit)

@Composable
private fun SectionHeader(title: String, editPill: SectionHeaderPill?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = title,
            color = NpicColors.Ink,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(600)),
            modifier = Modifier.weight(1f),
        )
        if (editPill != null) {
            Row(
                modifier = Modifier
                    .clip(NpicShapes.full)
                    .background(NpicColors.SaffronSoft)
                    .semantics { role = Role.Button }
                    .clickable(onClick = editPill.onClick)
                    .padding(horizontal = NpicSpacing.sm, vertical = NpicSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = NpicColors.SaffronDeep,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text  = "Edit",
                    color = NpicColors.SaffronDeep,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IconTextTile — icon-above-label 3-button row (m2056 Item 3)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact icon-above-label tile used for the add-media action rows on empty Photo /
 * Signature cards. Fits 3 equal-weight tiles across a 360dp phone without the label
 * wrapping ("Cap ture") that the horizontal-icon [NpicButton] shape produced at
 * this width (m2056 Item 3, screenshot in the user's report).
 */
@Composable
private fun IconTextTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalNpicChrome.current
    Column(
        modifier = modifier
            .clip(NpicShapes.sm)
            .background(NpicColors.SaffronSoft.copy(alpha = 0.55f))
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(vertical = NpicSpacing.sm, horizontal = NpicSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NpicColors.SaffronDeep,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text  = label,
            color = NpicColors.SaffronDeep,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
            maxLines = 1,
        )
    }
}
