package com.npic.photoandsignscanner.core.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Motion tokens: four durations and two easings.
 *
 * Source of truth: DESIGN.md §12 "Motion".
 *
 * Rule: never invent a duration. If a motion needs a value not on this list, either the
 * design intent maps to one of these buckets or the design needs a real reason to add one.
 *
 * Duration buckets by role:
 * - fast        150ms  taps, chip selections, toggles, hover-analog feedback
 * - standard    220ms  screen slides, most transitions, filter panel switches
 * - slow        320ms  bottom sheet reveal, dialog enter
 * - emphasized  400ms  the Camera→Edit shared-element transition, celebrations
 *
 * Easings:
 * - Ease-out cubic: default for anything the user initiates (decelerate into rest).
 * - Ease-in-out cubic: for continuous, self-driven motion (bidirectional pans, straighten).
 */
object NpicMotion {

    const val FastMs        = 150
    const val StandardMs    = 220
    const val SlowMs        = 320
    const val EmphasizedMs  = 400

    val EaseOutCubic: Easing   = CubicBezierEasing(0.33f, 1f,   0.68f, 1f)
    val EaseInOutCubic: Easing = CubicBezierEasing(0.65f, 0f,   0.35f, 1f)
    val EaseInCubic: Easing    = CubicBezierEasing(0.32f, 0f,   0.67f, 0f)

    fun <T> fast(easing: Easing = EaseOutCubic)       = tween<T>(FastMs,       easing = easing)
    fun <T> standard(easing: Easing = EaseOutCubic)   = tween<T>(StandardMs,   easing = easing)
    fun <T> slow(easing: Easing = EaseOutCubic)       = tween<T>(SlowMs,       easing = easing)
    fun <T> emphasized(easing: Easing = EaseInOutCubic) = tween<T>(EmphasizedMs, easing = easing)
}
