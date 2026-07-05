package com.npic.photoandsignscanner.core.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

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

    // ─── Spatial springs (M3 Expressive) ───────────────────────────────────
    // Springs are for SPATIAL properties only (scale, offset, size, bounds).
    // Color/alpha keep the tweens above — bouncing a color reads as a glitch.

    /** Press feedback, chip toggles, small selections. Settles fast, tiny overshoot. */
    fun <T> springSnappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.7f, stiffness = 400f)

    /** Layout/size changes, viewport resizes, tab pill slides. No visible bounce. */
    fun <T> springSmooth(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** Hero moments only: capture confirmation, save celebration, badge bumps. */
    fun <T> springBouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f)

    fun <T> springSnappyOrSnap(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else springSnappy()

    fun <T> springSmoothOrSnap(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else springSmooth()

    fun <T> springBouncyOrSnap(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else springBouncy()

    // ─── Reduce-motion aware spec builders ─────────────────────────────────
    // WCAG 2.3.3 / Android `AccessibilityManager.isRequestingReduceMotion` (API 33+).
    // Callers pull `LocalReduceMotion.current` and pass it to these functions; when true
    // the animation collapses to a `snap(0)` so users with vestibular sensitivity or
    // "Remove animations" enabled see instant state changes. Zero-duration snap is what
    // Compose recommends as the reduce-motion escape hatch (per animation-core docs).

    fun <T> fastOrSnap(reduce: Boolean, easing: Easing = EaseOutCubic) =
        if (reduce) snap<T>() else fast<T>(easing)

    fun <T> standardOrSnap(reduce: Boolean, easing: Easing = EaseOutCubic) =
        if (reduce) snap<T>() else standard<T>(easing)

    fun <T> slowOrSnap(reduce: Boolean, easing: Easing = EaseOutCubic) =
        if (reduce) snap<T>() else slow<T>(easing)

    fun <T> emphasizedOrSnap(reduce: Boolean, easing: Easing = EaseInOutCubic) =
        if (reduce) snap<T>() else emphasized<T>(easing)
}

/**
 * True when the user has "Remove animations" enabled in system a11y settings.
 *
 * Wired at `NpicTheme` root via [android.view.accessibility.AccessibilityManager.isEnabled]
 * combined with a `Settings.Global.TRANSITION_ANIMATION_SCALE == 0f` check (pre-API 33 proxy)
 * and `AccessibilityManager.isRequestingReduceMotion` on API 33+.
 *
 * Composables that animate should read this and gate via [NpicMotion.standardOrSnap] etc.,
 * OR wrap `AnimatedVisibility` / `AnimatedContent` with a manual bypass.
 *
 * Default `false` so previews and non-themed contexts animate normally.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
