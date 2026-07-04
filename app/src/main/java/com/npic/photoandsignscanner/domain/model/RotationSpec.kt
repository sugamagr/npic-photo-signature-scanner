package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * User rotation state from the Rotate tool. Two independent axes:
 *   • [quarterTurnsCw] — 90° clockwise steps in `0..3`. Applied first, discrete.
 *   • [straightenDegrees] — fine tilt in `-15f..+15f` for a mild horizon correction slider.
 *
 * They compose: final rotation = quarterTurnsCw × 90° + straightenDegrees.
 */
@Immutable
data class RotationSpec(
    val quarterTurnsCw: Int = 0,
    val straightenDegrees: Float = 0f,
) {
    init {
        require(quarterTurnsCw in 0..3) {
            "quarterTurnsCw must be 0..3, got $quarterTurnsCw"
        }
        require(straightenDegrees in STRAIGHTEN_MIN..STRAIGHTEN_MAX) {
            "straightenDegrees must be in $STRAIGHTEN_MIN..$STRAIGHTEN_MAX, got $straightenDegrees"
        }
    }

    val hasChanges: Boolean
        get() = quarterTurnsCw != 0 || straightenDegrees != 0f

    val totalDegrees: Float
        get() = quarterTurnsCw * 90f + straightenDegrees

    fun rotateCw(): RotationSpec = copy(quarterTurnsCw = (quarterTurnsCw + 1) % 4)
    fun rotateCcw(): RotationSpec = copy(quarterTurnsCw = (quarterTurnsCw + 3) % 4)
    fun withStraighten(degrees: Float): RotationSpec =
        copy(straightenDegrees = degrees.coerceIn(STRAIGHTEN_MIN, STRAIGHTEN_MAX))

    companion object {
        val NONE = RotationSpec()
        const val STRAIGHTEN_MIN = -15f
        const val STRAIGHTEN_MAX = +15f
    }
}
