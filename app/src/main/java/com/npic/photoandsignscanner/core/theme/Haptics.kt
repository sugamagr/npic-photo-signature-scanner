package com.npic.photoandsignscanner.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Thin wrapper over [LocalHapticFeedback] that respects the user's Settings-drawer
 * haptics toggle (user m1551 S3).
 *
 * Call sites use it exactly like they'd use `LocalHapticFeedback.current.performHapticFeedback`
 * — the only difference is that when `AppSettings.hapticsEnabled` is `false` (drawer toggle
 * off) the call is a no-op. That way a single flag in the drawer silences every haptic
 * anywhere in the app without touching each call site.
 */
@Composable
fun rememberNpicHaptics(): NpicHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalAppSettings.current.hapticsEnabled
    return NpicHaptics(feedback, enabled)
}

/**
 * Snapshot of haptic-feedback capability at a given composition. Callers cache one per
 * screen and call [performClick] / [performLongPress] on events. Instances are cheap:
 * they hold a reference to [HapticFeedback] and a boolean flag.
 */
@Stable
class NpicHaptics internal constructor(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    /** Short click tick — Camera shutter, chip toggles, quick confirmations. */
    fun performClick() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Long-press pulse — destructive confirms, multi-select entry, Save completion. */
    fun performLongPress() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
