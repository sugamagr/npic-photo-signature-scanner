package com.npic.photoandsignscanner.features.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.SortMode

/**
 * Gallery — the app's Home (DESIGN §7.6 + PRD §4.8).
 *
 * Layout stack, top-down:
 * 1. Custom top bar (96dp on Gallery, 56dp in selection mode) — Fraunces title, Search +
 *    Sort + Overflow icon buttons. Sort is a dropdown menu, not chips (per PRD §4.8).
 * 2. Horizontally scrollable class filter chip row with counts.
 * 3. Grid of NpicThumbnails, 3 columns on phones, contentPadding-bottom = 120dp so the
 *    last row clears the FAB (DESIGN §7.1).
 * 4. Persistent NpicCaptureFab at the bottom-center, sitting on a 32dp Ivory→transparent
 *    fade so grid rows never abut it. In selection mode this crossfades to an action bar.
 *
 * State comes from [GalleryViewModel]; navigation intents flow to the caller.
 */
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onCaptureClick: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onExportSelection: (Set<Long>) -> Unit,
    onDeleteSelection: (Set<Long>) -> Unit,
    onSearchClick: () -> Unit = {},
    onOverflowClick: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    GalleryContent(
        state               = state,
        onCaptureClick      = onCaptureClick,
        onRecordClick       = onRecordClick,
        onRecordLongPress   = viewModel::toggleSelect,
        onClassFilterChange = viewModel::setClassFilter,
        onSortModeChange    = viewModel::setSortMode,
        onClearSelection    = viewModel::clearSelection,
        onSelectAll         = viewModel::selectAll,
        onExportSelection   = onExportSelection,
        onDeleteSelection   = onDeleteSelection,
        onSearchClick       = onSearchClick,
        onOverflowClick     = onOverflowClick,
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
    onSelectAll: () -> Unit,
    onExportSelection: (Set<Long>) -> Unit,
    onDeleteSelection: (Set<Long>) -> Unit,
    onSearchClick: () -> Unit,
    onOverflowClick: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(NpicColors.Ivory)) {
        Column(Modifier.fillMaxSize()) {

            if (state.isSelectionMode) {
                GallerySelectionTopBar(
                    selectedCount   = state.selectedIds.size,
                    onCancel        = onClearSelection,
                    onSelectAll     = onSelectAll,
                )
            } else {
                GalleryTopBar(
                    sortMode        = state.sortMode,
                    onSortChange    = onSortModeChange,
                    onSearchClick   = onSearchClick,
                    onOverflowClick = onOverflowClick,
                )
            }

            ClassFilterRow(
                selected = state.classFilter,
                countsByClass = state.countsByClass,
                totalCount = state.totalCount,
                onSelect = onClassFilterChange,
            )

            Box(Modifier.fillMaxSize().weight(1f)) {
                if (state.isEmpty) {
                    if (state.hasAnyRecords) {
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
                            title = "No students yet",
                            body  = "Tap Capture to add your first.",
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

        // ─── Above-FAB gradient (DESIGN §7.1) + FAB / action bar ────────────
        FabRegion(
            selectionMode  = state.isSelectionMode,
            selectedCount  = state.selectedIds.size,
            onCapture      = onCaptureClick,
            onExport       = { onExportSelection(state.selectedIds) },
            onDelete       = { onDeleteSelection(state.selectedIds) },
            modifier       = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bars
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gallery top bar — 96dp large variant per DESIGN §6.3 + §7.6. Uses a custom implementation
 * because NpicTopBar is 56dp and doesn't currently support the large variant. Once large
 * variant support lands in NpicTopBar (Camera layer needs it too), migrate this to consume it.
 */
@Composable
private fun GalleryTopBar(
    sortMode: SortMode,
    onSortChange: (SortMode) -> Unit,
    onSearchClick: () -> Unit,
    onOverflowClick: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = NpicSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = "Gallery",
            color = NpicColors.Ink,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NpicIconButton(
                icon = Icons.Outlined.Search,
                contentDescription = "Search",
                onClick = onSearchClick,
            )
            Box {
                NpicIconButton(
                    icon = Icons.Outlined.SwapVert,
                    contentDescription = "Sort · currently ${sortMode.label}",
                    onClick = { sortMenuOpen = true },
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text  = { Text(mode.label) },
                            onClick = {
                                onSortChange(mode)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
            NpicIconButton(
                icon = Icons.Outlined.MoreVert,
                contentDescription = "More options",
                onClick = onOverflowClick,
            )
        }
    }
}

/**
 * Selection-mode top bar per DESIGN §7.6: `titleLarge` "N selected", Close (left), Select
 * all (right, ghost). Standard 56dp height because the display-title has been replaced by
 * a compact count.
 */
@Composable
private fun GallerySelectionTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = NpicSpacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NpicIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Cancel selection",
            onClick = onCancel,
        )
        Text(
            text  = "$selectedCount selected",
            color = NpicColors.Ink,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = NpicSpacing.xs),
        )
        NpicButton(
            label = "Select all",
            onClick = onSelectAll,
            style = NpicButtonStyle.Ghost,
            size  = NpicButtonSize.Small,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClassFilterRow(
    selected: ClassNum?,
    countsByClass: Map<ClassNum, Int>,
    totalCount: Int,
    onSelect: (ClassNum?) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = NpicSpacing.sm, vertical = NpicSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
    ) {
        NpicChip(
            label = "All · $totalCount",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        ClassNum.entries.forEach { c ->
            val n = countsByClass[c] ?: 0
            NpicChip(
                label = "Class ${c.label} · $n",
                selected = selected == c,
                onClick = { onSelect(c) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GalleryGrid(
    state: GalleryUiState,
    onRecordClick: (Long) -> Unit,
    onRecordLongPress: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start  = NpicSpacing.sm,
            end    = NpicSpacing.sm,
            top    = NpicSpacing.xs,
            // DESIGN §7.1 fixes grid bottom padding at 120dp so the last row clears the FAB.
            bottom = 120.dp,
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
// FAB region: gradient fade + FAB OR action bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FabRegion(
    selectionMode: Boolean,
    selectedCount: Int,
    onCapture: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 32dp Ivory→transparent fade above the region (DESIGN §7.1).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to NpicColors.Ivory,
                    ),
                ),
        )
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                fadeIn(tween(NpicMotion.StandardMs)) togetherWith fadeOut(tween(NpicMotion.StandardMs))
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(NpicColors.Ivory),
            label = "gallery_fab_bar",
        ) { selecting ->
            if (selecting) {
                // DESIGN §7.1: 72dp selection action bar, Export + Delete at 50% width each,
                // 12dp gap, 20dp side padding.
                NpicBottomBar {
                    NpicButton(
                        label     = "Export $selectedCount",
                        onClick   = onExport,
                        modifier  = Modifier.weight(1f),
                        startIcon = Icons.Outlined.Share,
                    )
                    NpicButton(
                        label     = "Delete",
                        onClick   = onDelete,
                        modifier  = Modifier.weight(1f),
                        style     = NpicButtonStyle.Destructive,
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
                    NpicCaptureFab(onClick = onCapture)
                }
            }
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
        val vm = remember {
            GalleryViewModel(com.npic.photoandsignscanner.data.repo.InMemoryStudentRepository())
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

@Preview(name = "Gallery — empty", showBackground = true)
@Composable
private fun GalleryPreviewEmpty() {
    NpicTheme {
        val vm = remember {
            GalleryViewModel(
                com.npic.photoandsignscanner.data.repo.InMemoryStudentRepository(seed = emptyList()),
            )
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

@Preview(name = "Gallery — selection mode", showBackground = true)
@Composable
private fun GalleryPreviewSelection() {
    NpicTheme {
        val vm = remember {
            GalleryViewModel(com.npic.photoandsignscanner.data.repo.InMemoryStudentRepository()).apply {
                toggleSelect(1); toggleSelect(4); toggleSelect(9)
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
