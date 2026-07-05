package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
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
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicElevation
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing

/**
 * Popup anchored menu — the NPIC replacement for Material 3's
 * [androidx.compose.material3.DropdownMenu] (m2056 Item 5).
 *
 * Container is [NpicColors.SurfaceRaised] with [NpicShapes.md] corners, level-3
 * shadow, and a 1 dp `borderSoft` hairline. Items are 44 dp min-height rows with
 * an optional 20 dp leading icon, `bodyMedium` label in [NpicColors.Ink], and an
 * optional trailing checkmark for selected state. Destructive items render with
 * `Terracotta` text and icon. Separators are 1 dp `borderSoft` dividers, inset by
 * the leading icon column so they don't cut across icon-less items.
 *
 * The caller anchors the menu by wrapping the trigger and the [NpicMenu] in the
 * same [Box], matching Material 3's positional contract — the popup opens at the
 * anchor's bottom edge.
 *
 * ## m2278 size + motion rewrite
 *
 * The initial implementation set `widthIn(min = minWidth)` with no max, and the
 * inner Column had `fillMaxWidth()`. Result on device: the popup stretched to
 * the full screen width because Popup gives its child unbounded width
 * constraints. Fix: `widthIn(min, max)` bounds the container, and the inner
 * Column uses `Modifier.width(IntrinsicSize.Max)` semantics by dropping
 * `fillMaxWidth()` — each item Row still fills the container but the container
 * itself measures to content.
 *
 * The initial implementation also had no entrance/exit motion — the popup
 * snapped in and out. Fix: [AnimatedVisibility] with a scaleIn + fadeIn from the
 * anchor's top-end corner (matches M3 Expressive menu motion). Gated on
 * [LocalReduceMotion] so users who dislike motion get the instant snap.
 *
 * Dismissal: tap outside, tap any non-destructive item's onClick, tap the
 * system-back key. The parent is responsible for clearing the `expanded` flag on
 * any of these paths.
 */
@Composable
fun NpicMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, NpicSpacing.xs),
    // m2410: menu measures to widest item + horizontal padding, no fixed max.
    // Was a fixed 240 dp cap in m2334; user reported menus with 2-3 short items
    // still looked too wide because the container filled its max regardless of
    // item length. Now Column.width(IntrinsicSize.Max) measures children and
    // collapses the container to `widest_child + horizontal padding` exactly.
    // The min = 200 dp floor prevents pathologically narrow menus with a single
    // short item like "Ok" — that would look like a cropped tooltip.
    minWidth: androidx.compose.ui.unit.Dp = 200.dp,
    content: NpicMenuScope.() -> Unit,
) {
    // MutableTransitionState so AnimatedVisibility runs the exit transition
    // before the Popup dismount — otherwise the popup disappears instantly the
    // moment `expanded` flips to false.
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = expanded
    val visuallyActive = transition.currentState || transition.targetState
    if (!visuallyActive) return

    val chrome = LocalNpicChrome.current
    val reduceMotion = LocalReduceMotion.current

    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
        offset = androidx.compose.ui.unit.IntOffset(0, 0),
    ) {
        val enterSpec = if (reduceMotion) tween<Float>(0) else tween<Float>(180)
        val exitSpec = if (reduceMotion) tween<Float>(0) else tween<Float>(140)
        AnimatedVisibility(
            visibleState = transition,
            enter = fadeIn(animationSpec = enterSpec) +
                scaleIn(
                    animationSpec = enterSpec,
                    initialScale = 0.92f,
                    // Anchor grows from its top-end corner so the animation reads as
                    // "sliding down from the icon that opened it" rather than the
                    // middle of the popup.
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
            exit = fadeOut(animationSpec = exitSpec) +
                scaleOut(
                    animationSpec = exitSpec,
                    targetScale = 0.94f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
        ) {
            Column(
                modifier = modifier
                    .padding(top = offset.y, start = offset.x)
                    .shadow(NpicElevation.level3, NpicShapes.md, clip = false)
                    .clip(NpicShapes.md)
                    .background(NpicColors.SurfaceRaised, NpicShapes.md)
                    .border(1.dp, chrome.borderSoft, NpicShapes.md)
                    // m2410: intrinsic width measures children (the item Rows) and
                    // sizes the Column to the widest one. widthIn(min = minWidth)
                    // just enforces the 200 dp floor. Result: menu is exactly as
                    // wide as its widest label + horizontal padding.
                    .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    .widthIn(min = minWidth)
                    .padding(vertical = NpicSpacing.xxs),
            ) {
                val scope = NpicMenuScopeImpl(
                    chrome = chrome,
                    onDismissRequest = onDismissRequest,
                )
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
                // m2410: item Row uses fillMaxWidth() so its clickable area covers
                // the whole container width, but the container itself (parent
                // Column with IntrinsicSize.Max) measures to the widest item's
                // INTRINSIC width — Compose's intrinsic-measurement pass ignores
                // fillMaxWidth and uses the natural content width instead. Net
                // result: menu is exactly as wide as the widest label + padding,
                // but each item's tap target still spans the full container.
                // The label Text also drops weight(1f) — inside an intrinsic-
                // sized parent, weight() collapses to zero width.
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
                    )
                    if (entry.selected) {
                        Spacer(Modifier.weight(1f, fill = false))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = NpicColors.SaffronDeep,
                            modifier = Modifier.size(18.dp),
                        )
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
