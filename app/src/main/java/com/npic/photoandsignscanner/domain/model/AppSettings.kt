package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * User-controlled preferences persisted via DataStore.
 *
 * Anchored to user directive m1551 S3 — a hamburger drawer from the Gallery
 * top bar surfaces two user-facing toggles plus a destructive Clear-all-data
 * affordance. This value object is the single source of truth every consumer
 * (theme, haptics helper) reads from.
 *
 * Motion is a tri-state so the user can override the system-level animation-
 * scale reading in either direction (some devices have accessibility scale
 * locked). Haptics is a Boolean because most call sites are one-shot LongPress
 * feedback.
 *
 * m2175 removed the `exportMimePreference` field. The app now always uses
 * `image/jpeg` for photo/combined single shares and `application/zip` for ZIP
 * bundles (the natural per-file MIME). The old JpegOnly override was an edge
 * case for portals that filter share targets by MIME — realistic users hit
 * "share single-record JPEG" not "share ZIP", so the override never mattered.
 */
@Immutable
data class AppSettings(
    val reduceMotionOverride: MotionPreference = MotionPreference.System,
    val hapticsEnabled: Boolean = true,
) {
    companion object {
        val Default = AppSettings()
    }
}

/**
 * Three-way override for the reduce-motion signal.
 *
 * `System` defers to the OS-level `Settings.Global.TRANSITION_ANIMATION_SCALE`
 * + `ANIMATOR_DURATION_SCALE` values (resolved in `NpicTheme.resolveReduceMotion`).
 * `On` forces reduce-motion even if the system is showing full animations —
 * useful for users who find our transitions distracting but haven't disabled
 * animations globally. `Off` forces full motion even if the OS reports
 * reduce-motion — useful during design QA on debug devices with animation
 * scale set to 0.
 */
enum class MotionPreference {
    System,
    On,
    Off,
}
