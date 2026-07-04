package com.npic.photoandsignscanner.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape tokens. Six radii cover every rounded surface in the app; anything not here is a bug.
 *
 * Source of truth: DESIGN.md §8 "Shape system".
 *
 * Usage rule: choose by role, not by pixel guess.
 * - xs  chips, tiny status pills, aspect-ratio chips
 * - sm  thumbnails, filter preview cells, small cards
 * - md  input fields, standard cards, mode capsules
 * - lg  large FABs, primary CTAs, image containers
 * - xl  bottom-sheet top corners, dialog corners
 * - full pill buttons, avatar rings, circular anchors
 */
object NpicShapes {

    val xs   = RoundedCornerShape(6.dp)
    val sm   = RoundedCornerShape(10.dp)
    val md   = RoundedCornerShape(14.dp)
    val lg   = RoundedCornerShape(20.dp)
    val xl   = RoundedCornerShape(28.dp)

    /** Practically-full pill; safer than Int.MAX_VALUE for pathological viewport sizes. */
    val full = RoundedCornerShape(999.dp)

    /** Bottom sheets: rounded only on the top corners. */
    val sheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Material 3 mapping — kept minimal so components pulled from Material don't drift. */
    val material = Shapes(
        extraSmall = xs,
        small      = sm,
        medium     = md,
        large      = lg,
        extraLarge = xl,
    )
}
