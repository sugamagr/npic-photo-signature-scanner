package com.npic.photoandsignscanner.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicElevation
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing

/**
 * Popup anchored menu — the NPIC replacement for Material 3's [androidx.compose.material3.DropdownMenu]
 * (m2056 Item 5).
 *
 * Container is [NpicColors.SurfaceRaised] with [NpicShapes.md] corners, level-3 shadow, and
 * a 1 dp `borderSoft` hairline. Items are 44 dp min-height rows with an optional 20 dp leading
 * icon in `inkMuted`, `bodyMedium` label in [NpicColors.Ink], and an optional trailing
 * checkmark for [NpicMenuItem.selected] state. Destructive items ([NpicMenuItem.destructive])
 * render with `Terracotta` text and icon. Separators are 1 dp `borderSoft` dividers between
 * groups, inset by the leading icon column.
 *
 * The caller anchors the menu by wrapping the trigger and the [NpicMenu] in the same [Box],
 * matching Material 3's positional contract — the popup opens at the anchor's bottom edge.
 *
 * Dismissal: tap outside, tap any non-destructive item's onClick, tap the system-back key.
 * The parent is responsible for clearing the `expanded` flag on any of these paths.
 */
@Composable
fun NpicMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, NpicSpacing.xs),
    minWidth: androidx.compose.ui.unit.Dp = 200.dp,
    content: NpicMenuScope.() -> Unit,
) {
    if (!expanded) return
    val chrome = LocalNpicChrome.current
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
        offset = androidx.compose.ui.unit.IntOffset(0, 0),
    ) {
        Box(
            modifier = modifier
                .padding(top = offset.y, start = offset.x)
                .shadow(NpicElevation.level3, NpicShapes.md, clip = false)
                .clip(NpicShapes.md)
                .background(NpicColors.SurfaceRaised, NpicShapes.md)
                .border(1.dp, chrome.borderSoft, NpicShapes.md)
                .widthIn(min = minWidth),
        ) {
            val scope = NpicMenuScopeImpl(chrome = chrome, onDismissRequest = onDismissRequest)
            Column(
                modifier = Modifier
                    .padding(vertical = NpicSpacing.xxs)
                    .fillMaxWidth(),
            ) {
                scope.content()
                scope.render()
            }
        }
    }
}

/** DSL scope for [NpicMenu] content. */
interface NpicMenuScope {
    fun item(
        label: String,
        onClick: () -> Unit,
        icon: ImageVector? = null,
        selected: Boolean = false,
        destructive: Boolean = false,
        dismissOnClick: Boolean = true,
    )

    fun divider()
}

/** Backing state for the DSL — accumulates items in insertion order then flushes them. */
@Immutable
private data class NpicMenuEntry(
    val kind: Kind,
    val label: String = "",
    val onClick: () -> Unit = {},
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val dismissOnClick: Boolean = true,
) {
    enum class Kind { Item, Divider }
}

private class NpicMenuScopeImpl(
    private val chrome: com.npic.photoandsignscanner.core.theme.NpicChrome,
    private val onDismissRequest: () -> Unit,
) : NpicMenuScope {

    private val entries = mutableListOf<NpicMenuEntry>()

    override fun item(
        label: String,
        onClick: () -> Unit,
        icon: ImageVector?,
        selected: Boolean,
        destructive: Boolean,
        dismissOnClick: Boolean,
    ) {
        entries += NpicMenuEntry(
            kind = NpicMenuEntry.Kind.Item,
            label = label,
            onClick = onClick,
            icon = icon,
            selected = selected,
            destructive = destructive,
            dismissOnClick = dismissOnClick,
        )
    }

    override fun divider() {
        entries += NpicMenuEntry(kind = NpicMenuEntry.Kind.Divider)
    }

    @Composable
    fun render() {
        // Whether any item in this menu carries a leading icon — the divider inset
        // aligns with the icon column so it doesn't cut across the icon-less items.
        val hasAnyIcon = entries.any { it.kind == NpicMenuEntry.Kind.Item && it.icon != null }
        val dividerInset = if (hasAnyIcon) NpicSpacing.md + 20.dp + NpicSpacing.sm else NpicSpacing.md

        entries.forEach { entry ->
            when (entry.kind) {
                NpicMenuEntry.Kind.Item -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .semantics { role = Role.Button }
                        .clickable {
                            entry.onClick()
                            if (entry.dismissOnClick) onDismissRequest()
                        }
                        .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
                ) {
                    val contentColor = when {
                        entry.destructive -> chrome.terracotta
                        entry.selected    -> NpicColors.SaffronDeep
                        else              -> NpicColors.Ink
                    }
                    if (hasAnyIcon) {
                        if (entry.icon != null) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Spacer(Modifier.width(20.dp))
                        }
                    }
                    Text(
                        text = entry.label,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (entry.selected) FontWeight(600) else FontWeight(500),
                        ),
                        modifier = Modifier
                            .let { m -> if (entry.selected) m else m.padding(end = NpicSpacing.sm) }
                            .semantics { role = Role.Button },
                    )
                    if (entry.selected) {
                        Spacer(Modifier.width(NpicSpacing.md))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = NpicColors.SaffronDeep,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                NpicMenuEntry.Kind.Divider -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = dividerInset, top = NpicSpacing.xxs, bottom = NpicSpacing.xxs)
                        .height(1.dp)
                        .background(chrome.borderSoft),
                )
            }
        }
    }
}

/** Dummy — suppresses "declared but never used" on the WindowInsets import above. */
@Suppress("unused")
private val _keepWindowInsetsUsage: WindowInsets = WindowInsets(0)
