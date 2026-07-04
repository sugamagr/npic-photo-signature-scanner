package com.npic.photoandsignscanner.core.theme

import androidx.compose.ui.unit.dp

/**
 * Elevation tokens. Four discrete levels; never interpolate.
 *
 * Source of truth: DESIGN.md §10 "Elevation".
 *
 * Rule: a surface uses EITHER a border OR a shadow — never both. If a card needs both,
 * the design has ambiguous hierarchy and needs review.
 *
 * Practical mapping:
 * - level0  flat on Ivory (list rows, section headers)
 * - level1  gentle lift: chips, non-interactive cards
 * - level2  interactive cards, bottom bars, sliders' thumbs
 * - level3  primary CTAs, dialogs, capture FAB, magnifier bubble
 */
object NpicElevation {

    val level0 = 0.dp
    val level1 = 2.dp
    val level2 = 6.dp
    val level3 = 12.dp
}
