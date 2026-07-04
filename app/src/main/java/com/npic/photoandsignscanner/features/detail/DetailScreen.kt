package com.npic.photoandsignscanner.features.detail

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
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
    onCapturePhoto: () -> Unit,
    onImportPhoto: () -> Unit,
    onCaptureSignature: () -> Unit,
    onDrawSignature: () -> Unit,
    onImportSignature: () -> Unit,
    onExport: (StudentRecord) -> Unit,
    onDuplicateToAnotherClass: (StudentRecord) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.notFound) {
        if (state.notFound) currentOnBack()
    }

    Column(Modifier.fillMaxSize().background(NpicColors.Ivory)) {
        DetailTopBar(
            title = state.record?.headerTitle ?: "Loading…",
            onBack = onBack,
            onEdit = { state.record?.let { onEditPhoto(it) } },
            onDelete = { showDeleteConfirm = true },
            onDuplicateToClass = { state.record?.let { onDuplicateToAnotherClass(it) } },
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
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate to another class") },
                        onClick = { menuOpen = false; onDuplicateToClass() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = NpicColors.Terracotta) },
                        onClick = { menuOpen = false; onDelete() },
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
    onCapturePhoto: () -> Unit,
    onImportPhoto: () -> Unit,
    onCaptureSignature: () -> Unit,
    onDrawSignature: () -> Unit,
    onImportSignature: () -> Unit,
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
            onCapture = onCapturePhoto,
            onImport = onImportPhoto,
        )
        SignatureCard(
            record = record,
            onEdit = { onEditSignature(record) },
            onCapture = onCaptureSignature,
            onDraw = onDrawSignature,
            onImport = onImportSignature,
        )
    }
}

@Composable
private fun MetadataCard(record: StudentRecord) {
    NpicCard(
        padding = PaddingValues(NpicSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.lg)) {
                MetadataCell("Class", "Class ${record.classNum.label}", Modifier.weight(1f))
                MetadataCell("Serial", record.serialLabel, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.lg)) {
                MetadataCell("Captured", record.createdAtLabel, Modifier.weight(1f))
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
    val chrome = LocalNpicChrome.current
    NpicCard(
        style   = com.npic.photoandsignscanner.core.ui.NpicCardStyle.Flat,
        onClick = if (hasPhoto) onEdit else null,
        padding = PaddingValues(NpicSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
            Text(
                text  = "Photo",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(600)),
            )
            if (hasPhoto) {
                PhotoPlaceholder(path = record.photoPath)
                Text(
                    text  = "Tap to edit",
                    color = chrome.inkMuted,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
                )
            } else {
                DashedPlaceholder(
                    aspectRatio = 0.8f,
                    label = "No photo yet",
                    icon = Icons.Outlined.CameraAlt,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
                ) {
                    NpicButton(
                        label     = "Capture",
                        onClick   = onCapture,
                        modifier  = Modifier.weight(1f),
                        style     = NpicButtonStyle.Secondary,
                        size      = com.npic.photoandsignscanner.core.ui.NpicButtonSize.Small,
                        startIcon = Icons.Outlined.CameraAlt,
                    )
                    NpicButton(
                        label     = "Import",
                        onClick   = onImport,
                        modifier  = Modifier.weight(1f),
                        style     = NpicButtonStyle.Ghost,
                        size      = com.npic.photoandsignscanner.core.ui.NpicButtonSize.Small,
                        startIcon = Icons.Outlined.PhotoLibrary,
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
    val chrome = LocalNpicChrome.current
    NpicCard(
        onClick = if (hasSig) onEdit else null,
        padding = PaddingValues(NpicSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm)) {
            Text(
                text  = "Signature",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(600)),
            )
            if (hasSig) {
                SignaturePlaceholder(path = record.signaturePath.orEmpty())
                Text(
                    text  = "Tap to edit",
                    color = chrome.inkMuted,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
                )
            } else {
                DashedPlaceholder(
                    aspectRatio = 3f,
                    label = "No signature yet",
                    icon = Icons.Outlined.Draw,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
                ) {
                    NpicButton(
                        label     = "Capture",
                        onClick   = onCapture,
                        modifier  = Modifier.weight(1f),
                        style     = NpicButtonStyle.Secondary,
                        size      = com.npic.photoandsignscanner.core.ui.NpicButtonSize.Small,
                        startIcon = Icons.Outlined.CameraAlt,
                    )
                    NpicButton(
                        label     = "Draw",
                        onClick   = onDraw,
                        modifier  = Modifier.weight(1f),
                        style     = NpicButtonStyle.Ghost,
                        size      = com.npic.photoandsignscanner.core.ui.NpicButtonSize.Small,
                        startIcon = Icons.Outlined.Draw,
                    )
                    NpicButton(
                        label     = "Import",
                        onClick   = onImport,
                        modifier  = Modifier.weight(1f),
                        style     = NpicButtonStyle.Ghost,
                        size      = com.npic.photoandsignscanner.core.ui.NpicButtonSize.Small,
                        startIcon = Icons.Outlined.PhotoLibrary,
                    )
                }
            }
        }
    }
}

/**
 * Present-photo card image (DESIGN §7.7 8:10 aspect). Bug#1+#2: loads the SourceStore
 * asset via Coil. Falls back to a SaffronSoft-tinted placeholder if the file is missing.
 */
@Composable
private fun PhotoPlaceholder(path: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f)
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
    val canExportCombined = record.photoPath.isNotBlank() && record.hasSignature
    val label = if (canExportCombined) "Export" else "Export (needs both media)"
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
            TextButton(onClick = onDelete) {
                Text("Delete", color = NpicColors.Terracotta)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text("Keep", color = NpicColors.Saffron)
            }
        },
    )
}
