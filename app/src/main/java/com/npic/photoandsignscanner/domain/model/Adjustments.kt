package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * User-tunable image corrections applied on top of a [FilterPreset]. All five channels are
 * bipolar integers in `-50..+50`; `0` means "no change". Kept as `Int` (not `Float`) because
 * DESIGN §7.3 spec renders the value bubble as tabular-lining digits with no decimal point,
 * and the OpenCV pipeline (PRD §5) receives the same integer step directly.
 *
 * Composition rule (PRD §4.5): filter is applied FIRST, then these deltas.
 */
@Immutable
data class Adjustments(
    val brightness: Int = 0,
    val contrast:   Int = 0,
    val sharpness:  Int = 0,
    val saturation: Int = 0,
    val warmth:     Int = 0,
) {
    val hasChanges: Boolean
        get() = brightness != 0 || contrast != 0 || sharpness != 0 || saturation != 0 || warmth != 0

    companion object {
        val NONE = Adjustments()
        const val MIN = -50
        const val MAX = +50
    }
}
