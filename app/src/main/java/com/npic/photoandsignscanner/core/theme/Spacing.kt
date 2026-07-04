package com.npic.photoandsignscanner.core.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens on a 4dp grid.
 *
 * Source of truth: DESIGN.md §9 "Spacing".
 *
 * Rule: never pass a raw `.dp` literal for padding/margin/gap in feature code. If you need
 * a value not on this list, either the design is off-grid (fix the design) or a new token
 * needs to be added here first.
 *
 * The one deliberate exception is component-internal layout math (e.g. a border stroke
 * that must be exactly 2dp to match a spec) — those live inside core/ui/ components and
 * are documented at the call site.
 */
object NpicSpacing {

    /** 4dp — hairline breathing room, chip inner padding. */
    val xxs = 4.dp

    /** 8dp — icon-to-label gap, tight row inner padding. */
    val xs  = 8.dp

    /** 12dp — filter cell inter-spacing, inline field-to-field gap. */
    val sm  = 12.dp

    /** 16dp — default screen horizontal padding, standard section padding. */
    val md  = 16.dp

    /** 20dp — image viewport inset in Edit screen. */
    val lg  = 20.dp

    /** 24dp — section separator, prominent gaps between grouped rows. */
    val xl  = 24.dp

    /** 32dp — top-of-screen breathing room after status bar. */
    val xxl = 32.dp

    /** 40dp — reserved for hero sections, empty-state offsets. */
    val xxxl = 40.dp
}
