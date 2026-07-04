package com.npic.photoandsignscanner.features.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.core.ui.NpicBottomBar
import com.npic.photoandsignscanner.core.ui.NpicButton
import com.npic.photoandsignscanner.core.ui.NpicButtonSize
import com.npic.photoandsignscanner.core.ui.NpicButtonStyle
import com.npic.photoandsignscanner.core.ui.NpicCaptureFab
import com.npic.photoandsignscanner.core.ui.NpicChip
import com.npic.photoandsignscanner.core.ui.NpicEmptyState
import com.npic.photoandsignscanner.core.ui.NpicIconButton
import com.npic.photoandsignscanner.core.ui.NpicIconButtonStyle
import com.npic.photoandsignscanner.core.ui.NpicThumbnail
import com.npic.photoandsignscanner.core.ui.NpicTopBar
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SortMode

/**
 * Gallery screen — the app's Home. Grid of records + persistent Capture FAB. Long-press a
 * thumbnail to enter selection mode; FAB crossfades into a bottom-action bar (Export /
 * Delete) as per DESIGN §6.1 and PRD §5.6.
 *
 * State comes from [GalleryViewModel]. Callbacks flow up to the caller so navigation stays
 * in one place (MainActivity).
 */
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onCaptureClick: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onExportSelection: (Set<Long>) -> Unit,
    onDeleteSelection: (Set<Long>) -> Unit,
    onOverflowClick: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    GalleryContent(
        state             = state,
        onCaptureClick    = onCaptureClick,
        onRecordClick     = onRecordClick,
        onRecordLongPress = viewModel::toggleSelect,
        onClassFilterChange = viewModel::setClassFilter,
        onSortModeChange  = viewModel::setSortMode,
        onClearSelection  = viewModel::clearSelection,
        onExportSelection = onExportSelection,
        onDeleteSelection = onDeleteSelection,
        onOverflowClick   = onOverflowClick,
    )
}

@Composable
private fun GalleryContent(
    state: GalleryUiState,
    onCaptureClick: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onRecordLongPress: (Long) -> Unit,
    onClassFilterChange: (ClassNum?) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onClearSelection: () -> Unit,
    onExportSelection: (Set<Long>) -> Unit,
    onDeleteSelection: (Set<Long>) -> Unit,
    onOverflowClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current

    Box(Modifier.fillMaxSize().background(NpicColors.Ivory)) {
        Column(Modifier.fillMaxSize()) {

            // ─── Top bar ───────────────────────────────────────────────────
            if (state.isSelectionMode) {
                NpicTopBar(
                    title       = "${state.selectedIds.size} selected",
                    navIcon     = Icons.Outlined.SwapVert, // used as a "cancel" glyph pending final icon
                    onNavClick  = onClearSelection,
                    navContentDescription = "Cancel selection",
                )
            } else {
                NpicTopBar(
                    title       = "NPIC Scanner",
                    action1Icon = Icons.Outlined.MoreVert,
                    action1ContentDescription = "More options",
                    onAction1Click = onOverflowClick,
                )
            }

            // ─── Filter + sort row ─────────────────────────────────────────
            FilterAndSortRow(
                classFilter = state.classFilter,
                sortMode    = state.sortMode,
                onClassFilterChange = onClassFilterChange,
                onSortModeChange    = onSortModeChange,
            )

            // ─── Header count ──────────────────────────────────────────────
            if (state.hasAnyRecords) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = if (state.classFilter != null)
                                    "${state.records.size} of ${state.totalCount}"
                                else
                                    "${state.records.size} records",
                        color = chrome.inkMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // ─── Body: empty state or grid ─────────────────────────────────
            Box(Modifier.fillMaxSize().weight(1f)) {
                if (state.isEmpty) {
                    if (state.hasAnyRecords) {
                        // filtered to nothing
                        NpicEmptyState(
                            title = "No records for Class ${state.classFilter?.label ?: ""}",
                            body  = "Try a different class filter or clear it.",
                            action = {
                                NpicButton(
                                    "Show all",
                                    onClick = { onClassFilterChange(null) },
                                    style   = NpicButtonStyle.Secondary,
                                    size    = NpicButtonSize.Small,
                                )
                            },
                        )
                    } else {
                        NpicEmptyState(
                            title = "No records yet",
                            body  = "Tap Capture to scan the first passport photo. Signatures come next.",
                        )
                    }
                } else {
                    GalleryGrid(
                        state = state,
                        onRecordClick = onRecordClick,
                        onRecordLongPress = onRecordLongPress,
                    )
                }
            }
        }

        // ─── Persistent Capture FAB / Selection action bar ─────────────────
        AnimatedContent(
            targetState = state.isSelectionMode,
            transitionSpec = {
                (fadeIn(tween(NpicMotion.StandardMs)) togetherWith fadeOut(tween(NpicMotion.StandardMs)))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            label = "gallery_fab_bar",
        ) { selecting ->
            if (selecting) {
                NpicBottomBar {
                    NpicButton(
                        label = "Export ${state.selectedIds.size}",
                        onClick = { onExportSelection(state.selectedIds) },
                        modifier = Modifier.weight(1f),
                        startIcon = Icons.Outlined.Share,
                    )
                    NpicButton(
                        label = "Delete",
                        onClick = { onDeleteSelection(state.selectedIds) },
                        style = NpicButtonStyle.Destructive,
                        startIcon = Icons.Outlined.Delete,
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = NpicSpacing.xl, top = NpicSpacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    NpicCaptureFab(onClick = onCaptureClick)
                }
            }
        }
    }
}

@Composable
private fun FilterAndSortRow(
    classFilter: ClassNum?,
    sortMode: SortMode,
    onClassFilterChange: (ClassNum?) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
    ) {
        NpicChip(
            label = "All",
            selected = classFilter == null,
            onClick = { onClassFilterChange(null) },
        )
        ClassNum.entries.forEach { c ->
            NpicChip(
                label = "Class ${c.label}",
                selected = classFilter == c,
                onClick = { onClassFilterChange(c) },
            )
        }
        // Divider spacer — 8dp so the sort chip visually detaches.
        Box(Modifier.size(width = NpicSpacing.sm, height = 1.dp))
        SortMode.entries.forEach { s ->
            NpicChip(
                label = s.label,
                selected = sortMode == s,
                onClick = { onSortModeChange(s) },
            )
        }
    }
}

@Composable
private fun GalleryGrid(
    state: GalleryUiState,
    onRecordClick: (Long) -> Unit,
    onRecordLongPress: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start  = NpicSpacing.md,
            end    = NpicSpacing.md,
            top    = NpicSpacing.xs,
            bottom = 112.dp, // leave room for the FAB / selection bar
        ),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        verticalArrangement   = Arrangement.spacedBy(NpicSpacing.sm),
    ) {
        items(items = state.records, key = { it.id }) { record ->
            val selected = record.id in state.selectedIds
            NpicThumbnail(
                classLabel = record.classNum.label,
                selected = selected,
                missingSignature = !record.hasSignature,
                onClick = {
                    if (state.isSelectionMode) onRecordLongPress(record.id)
                    else onRecordClick(record.id)
                },
                onLongPress = { onRecordLongPress(record.id) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Gallery — populated", showBackground = true)
@Composable
private fun GalleryPreviewPopulated() {
    NpicTheme {
        val vm = remember { GalleryViewModel() }
        GalleryScreen(
            viewModel = vm,
            onCaptureClick = {},
            onRecordClick = {},
            onExportSelection = {},
            onDeleteSelection = {},
        )
    }
}

@Preview(name = "Gallery — empty (no records)", showBackground = true)
@Composable
private fun GalleryPreviewEmpty() {
    NpicTheme {
        val vm = remember { GalleryViewModel(seed = emptyList()) }
        GalleryScreen(
            viewModel = vm,
            onCaptureClick = {},
            onRecordClick = {},
            onExportSelection = {},
            onDeleteSelection = {},
        )
    }
}

@Preview(name = "Gallery — selection mode", showBackground = true)
@Composable
private fun GalleryPreviewSelection() {
    NpicTheme {
        val vm = remember {
            GalleryViewModel().apply {
                toggleSelect(1)
                toggleSelect(4)
                toggleSelect(9)
            }
        }
        GalleryScreen(
            viewModel = vm,
            onCaptureClick = {},
            onRecordClick = {},
            onExportSelection = {},
            onDeleteSelection = {},
        )
    }
}
