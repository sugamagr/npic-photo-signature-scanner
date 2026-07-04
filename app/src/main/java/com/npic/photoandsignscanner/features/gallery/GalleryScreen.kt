package com.npic.photoandsignscanner.features.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
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
    onRecordClick: (String) -> Unit,
    onExportSelection: (Set<String>) -> Unit,
    onDeleteSelection: (Set<String>) -> Unit,
    onSearchClick: () -> Unit = {},
    onOverflowClick: () -> Unit = {},
) {
    // Oracle m-8c1 consistency: lifecycle-aware collection stops StateFlow work when the
    // Gallery is off-screen (Camera/Edit/Save/Detail cover it). Matches Detail/Edit/Save.
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    onRecordClick: (String) -> Unit,
    onRecordLongPress: (String) -> Unit,
    onClassFilterChange: (ClassNum?) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onExportSelection: (Set<String>) -> Unit,
    onDeleteSelection: (Set<String>) -> Unit,
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
                        // Filtered-empty variant: user has records, current class filter matches none.
                        // Uses generic NpicEmptyState because the situation is transient — clearing
                        // the filter restores the grid immediately.
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
                        // Bug#11: the true zero-state is the FIRST screen a new user sees. It
                        // deserves a purpose-built layout — layered brand illustration, hierarchy
                        // (headline → supporting copy → hint tiles), and a strong primary CTA —
                        // rather than the utility NpicEmptyState used for filtered empties.
                        GalleryZeroState(onCapture = onCaptureClick)
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
            .windowInsetsPadding(WindowInsets.statusBars)
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
                    // Bug#14: AutoMirrored.Outlined.Sort reads as "sort" at a glance —
                    // SwapVert reads as "swap/reorder" and confused users on device.
                    icon = Icons.AutoMirrored.Outlined.Sort,
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
            .windowInsetsPadding(WindowInsets.statusBars)
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
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClassFilterTile(
            title = "All",
            count = totalCount,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        ClassNum.entries.forEach { c ->
            ClassFilterTile(
                title = "Class ${c.label}",
                count = countsByClass[c] ?: 0,
                selected = selected == c,
                onClick = { onSelect(c) },
            )
        }
    }
}

/**
 * Bug#12: replaces the single-line "Class X · N" chip with a two-line tile — the count
 * dominates as a display numeral, the class label whispers beneath. Selection swaps the
 * container to SaffronSoft with a Saffron border, matching [NpicChip] tokens so both
 * affordances share the design system.
 */
@Composable
private fun ClassFilterTile(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val container = if (selected) chrome.saffronSoft else NpicColors.Surface
    val border    = if (selected) NpicColors.Saffron else chrome.borderStrong
    val titleColor = if (selected) NpicColors.SaffronDeep else chrome.inkMuted
    val countColor = NpicColors.Ink
    Column(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = "$title, $count ${if (count == 1) "record" else "records"}"
            }
            .clip(com.npic.photoandsignscanner.core.theme.NpicShapes.sm)
            .background(container, com.npic.photoandsignscanner.core.theme.NpicShapes.sm)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = border,
                shape = com.npic.photoandsignscanner.core.theme.NpicShapes.sm,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.xs)
            .defaultMinSize(minWidth = 68.dp, minHeight = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text  = count.toString(),
            color = countColor,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight(700)),
        )
        Text(
            text  = title,
            color = titleColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(500)),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Zero-state (Bug#11) — the first screen a fresh install renders
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bug#11 hero empty state. Not a generic fallback — this is a designed onboarding surface.
 *
 * Layers, top to bottom:
 *  1. Emblem: 3 overlapping rounded thumbnails, back-to-front, subtle rotation, tinted with
 *     Saffron/SaffronSoft/Ivory so the composition reads as a stack of student cards. This
 *     replaces the utility "icon-in-a-circle" pattern that shipped before.
 *  2. Headline (displaySmall, Ink) — "Start scanning." Anchors visual weight.
 *  3. Supporting sentence (bodyLarge, InkMuted) — one line, no jargon.
 *  4. Two hint tiles (photo / signature) laid out horizontally; leading icon + label + tiny
 *     description. Introduces the two capture modes without cluttering the CTA.
 *  5. Primary CTA — full-width [NpicButton] labeled "Capture your first photo".
 */
@Composable
private fun GalleryZeroState(onCapture: () -> Unit) {
    val chrome = LocalNpicChrome.current
    Box(
        modifier = Modifier.fillMaxSize().padding(NpicSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NpicSpacing.md),
        ) {
            ZeroStateEmblem()

            Spacer(Modifier.height(NpicSpacing.xxs))

            Text(
                text  = "Start scanning.",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text  = "Point, capture, sign — every passport photo lands here, portal-ready.",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(NpicSpacing.xxs))

            Row(
                horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ZeroStateHintTile(
                    icon  = Icons.Outlined.CameraAlt,
                    title = "Photo",
                    body  = "Auto edge + filter",
                    modifier = Modifier.weight(1f),
                )
                ZeroStateHintTile(
                    icon  = Icons.Outlined.Draw,
                    title = "Signature",
                    body  = "Draw or scan ink",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(NpicSpacing.xs))

            NpicButton(
                label   = "Capture your first photo",
                onClick = onCapture,
                modifier = Modifier.fillMaxWidth(),
                startIcon = Icons.Outlined.CameraAlt,
            )
        }
    }
}

/**
 * Stacked-cards emblem for the zero-state. Three 88×112 rounded rectangles fanned out by
 * ±6° and offset by 16dp — a schematic of the eventual student gallery. Draws with a Canvas
 * for pixel control while keeping tokens; no bitmap asset required.
 */
@Composable
private fun ZeroStateEmblem() {
    val chrome = LocalNpicChrome.current
    Box(
        modifier = Modifier.size(width = 200.dp, height = 148.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val cardW = 88.dp.toPx()
            val cardH = 112.dp.toPx()
            val radius = 20.dp.toPx()
            val strokeW = 1.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            data class Card(val dx: Float, val angle: Float, val fill: Color, val border: Color)
            val cards = listOf(
                Card(-44.dp.toPx(), -8f, chrome.saffronSoft.copy(alpha = 0.55f), chrome.borderSoft),
                Card(  0.dp.toPx(),  0f, NpicColors.Surface,                     chrome.borderStrong),
                Card( 44.dp.toPx(),  8f, chrome.saffronSoft,                     NpicColors.Saffron),
            )
            cards.forEach { card ->
                rotate(card.angle, pivot = androidx.compose.ui.geometry.Offset(cx + card.dx, cy)) {
                    val topLeft = androidx.compose.ui.geometry.Offset(
                        x = cx + card.dx - cardW / 2f,
                        y = cy - cardH / 2f,
                    )
                    val cardSize = androidx.compose.ui.geometry.Size(cardW, cardH)
                    drawRoundRect(
                        color = card.fill,
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    )
                    drawRoundRect(
                        color = card.border,
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW),
                    )
                }
            }
        }
        // Saffron accent dot on the front card — sells "signature captured" without labels.
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(com.npic.photoandsignscanner.core.theme.NpicShapes.full)
                .background(NpicColors.Saffron)
                .align(Alignment.BottomEnd)
                .padding(0.dp),
        )
    }
}

@Composable
private fun ZeroStateHintTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalNpicChrome.current
    Column(
        modifier = modifier
            .clip(com.npic.photoandsignscanner.core.theme.NpicShapes.sm)
            .background(NpicColors.Surface)
            .border(
                width = 1.dp,
                color = chrome.borderSoft,
                shape = com.npic.photoandsignscanner.core.theme.NpicShapes.sm,
            )
            .padding(NpicSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NpicColors.Saffron,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text  = title,
            color = NpicColors.Ink,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(600)),
        )
        Text(
            text  = body,
            color = chrome.inkMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GalleryGrid(
    state: GalleryUiState,
    onRecordClick: (String) -> Unit,
    onRecordLongPress: (String) -> Unit,
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
                content = {
                    // Bug#1+#2: render the saved photo from SourceStore (`filesDir/sources/`).
                    // Coil handles missing-file / decode failure by leaving the slot blank,
                    // which falls back to the NpicThumbnail card chrome.
                    if (record.photoPath.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = java.io.File(record.photoPath),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
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
        val reduceMotion = LocalReduceMotion.current
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                fadeIn(NpicMotion.standardOrSnap(reduceMotion)) togetherWith
                    fadeOut(NpicMotion.standardOrSnap(reduceMotion))
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
                        .windowInsetsPadding(WindowInsets.navigationBars)
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
                // Match MockGalleryData's deterministic UUID-shaped ids for records 1, 4, 9.
                toggleSelect("00000000-0000-0000-0000-000000000001")
                toggleSelect("00000000-0000-0000-0000-000000000004")
                toggleSelect("00000000-0000-0000-0000-000000000009")
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
