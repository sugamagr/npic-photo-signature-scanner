package com.npic.photoandsignscanner.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.ui.NpicIconButton
import com.npic.photoandsignscanner.domain.model.StudentRecord

/**
 * Bug#15 Gallery search. Full-screen text query over students by displayName, serial
 * number (digits typed anywhere), and class label. Backed by [SearchViewModel] which
 * client-side-filters the [StudentRepository] Flow.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onRecordClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchContent(
        query = state.query,
        results = state.results,
        hasQuery = state.hasQuery,
        isEmpty = state.isEmpty,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::clearQuery,
        onBack = onBack,
        onRecordClick = onRecordClick,
    )
}

@Composable
private fun SearchContent(
    query: String,
    results: List<StudentRecord>,
    hasQuery: Boolean,
    isEmpty: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    onRecordClick: (String) -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Box(Modifier.fillMaxSize().background(NpicColors.Ivory)) {
        Column(Modifier.fillMaxSize()) {
            SearchTopBar(
                query = query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onBack = onBack,
            )
            when {
                !hasQuery -> SearchHint()
                isEmpty   -> SearchEmpty(query = query)
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup(),
                    contentPadding = PaddingValues(
                        horizontal = NpicSpacing.md,
                        vertical = NpicSpacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
                ) {
                    items(items = results, key = { it.id }) { record ->
                        // qc-round-12 Oracle #6 MINOR #10: wrap onRecordClick via
                        // rememberUpdatedState inside the items block so lambda identity
                        // stays stable across recompositions of the row. Matches the
                        // GalleryScreen:623-626 O(N) recomp fix pattern. m1597 industry
                        // standard anti-regression trail.
                        val currentOnRecordClick by rememberUpdatedState(onRecordClick)
                        SearchResultRow(
                            record = record,
                            onClick = { currentOnRecordClick(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val focusRequester = remember { FocusRequester() }
    // m2334: rewrote to use Material 3 OutlinedTextField (was BasicTextField). Both
    // m2132's delay(50) fix and m2278's decorationBox fix failed on device — user
    // reported search input still dead after both. This diagnostic swap tells us
    // whether the bug is in our custom BasicTextField wrapper (would work with
    // OutlinedTextField's proven IME wiring) or somewhere else entirely (nav /
    // permission / device-level IME issue — would fail even here).
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(72.dp)
            .padding(horizontal = NpicSpacing.sm, vertical = NpicSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
    ) {
        NpicIconButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { newValue ->
                android.util.Log.d("SearchScreen", "onQueryChange fired: '$newValue'")
                onQueryChange(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text  = "Name, serial, or class",
                    color = chrome.inkFaint,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = chrome.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(NpicShapes.full)
                            .clickable(onClick = onClearQuery)
                            .semantics { role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = chrome.inkMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else null,
            singleLine = true,
            shape = NpicShapes.sm,
            textStyle = LocalTextStyle.current.copy(
                color = NpicColors.Ink,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Search,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor            = NpicColors.Ink,
                unfocusedTextColor          = NpicColors.Ink,
                focusedContainerColor       = NpicColors.Surface,
                unfocusedContainerColor     = NpicColors.Surface,
                cursorColor                 = NpicColors.Saffron,
                focusedBorderColor          = NpicColors.Saffron,
                unfocusedBorderColor        = chrome.borderStrong,
                focusedPlaceholderColor     = chrome.inkFaint,
                unfocusedPlaceholderColor   = chrome.inkFaint,
                focusedLeadingIconColor     = chrome.inkMuted,
                unfocusedLeadingIconColor   = chrome.inkMuted,
                focusedTrailingIconColor    = chrome.inkMuted,
                unfocusedTrailingIconColor  = chrome.inkMuted,
            ),
        )
    }
}

@Composable
private fun SearchHint() {
    val chrome = LocalNpicChrome.current
    Box(
        modifier = Modifier.fillMaxSize().padding(NpicSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(NpicShapes.full)
                    .background(chrome.saffronSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = NpicColors.Saffron,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(NpicSpacing.xxs))
            Text(
                text  = "Search students",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text  = "Type a name, class number, or serial. Matches update as you type.",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchEmpty(query: String) {
    val chrome = LocalNpicChrome.current
    Box(
        modifier = Modifier.fillMaxSize().padding(NpicSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
        ) {
            Text(
                text  = "No matches for \u201C$query\u201D",
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text  = "Try a different spelling or clear the filter.",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    record: StudentRecord,
    onClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NpicShapes.sm)
            .background(NpicColors.Surface)
            .border(1.dp, chrome.borderSoft, NpicShapes.sm)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(NpicSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(NpicShapes.sm)
                .background(chrome.saffronSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = record.classNum.label,
                color = NpicColors.SaffronDeep,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(700)),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text  = record.displayName.ifBlank { "Serial ${record.serial}" },
                color = NpicColors.Ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(600)),
            )
            Text(
                text  = "Class ${record.classNum.label} \u00b7 Serial ${record.serial}",
                color = chrome.inkMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (!record.hasSignature) {
            Text(
                text  = "no sig",
                color = NpicColors.Terracotta,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(600)),
            )
        }
    }
}
