package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * User-controlled preferences persisted via DataStore.
 *
 * Anchored to user directive m1551 S3 — a hamburger drawer from the Gallery
 * top bar surfaces three user-facing toggles plus a destructive Clear-all-data
 * affordance. This value object is the single source of truth every consumer
 * (theme, haptics helper, export share launcher) reads from.
 *
 * The three preferences are intentionally coarse: enum-of-three for motion so
 * the user can override the system-level animation-scale reading in either
 * direction (some devices have accessibility scale locked); Boolean for
 * haptics because most call sites are one-shot LongPress feedback; enum-of-two
 * for export MIME because the receiver-app compatibility matrix collapses to
 * "let the receiver pick" vs "force image/jpeg".
 */
@Immutable
data class AppSettings(
    val reduceMotionOverride: MotionPreference = MotionPreference.System,
    val hapticsEnabled: Boolean = true,
    val exportMimePreference: ExportMime = ExportMime.Auto,
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

/**
 * Export MIME override for share intents.
 *
 * `Auto` lets our default logic pick (photo/combined singles → image/jpeg;
 * ZIP bundles → application/zip). `JpegOnly` forces image/jpeg for every
 * non-ZIP share, matching apps that only accept image intents (e.g. some
 * WhatsApp Business builds reject image wildcard but accept the concrete type).
 */
enum class ExportMime {
    Auto,
    JpegOnly,
}
