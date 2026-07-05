package com.npic.photoandsignscanner.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * m2508: shape of `version.json` served from
 * `raw.githubusercontent.com/sugamagr/npic-photo-signature-scanner/main/version.json`.
 *
 * The manifest lives at a stable URL (not a per-release tag), so the app fetches the
 * same path every launch and compares `versionCode` against `BuildConfig.VERSION_CODE`.
 * Fields are stable across releases; anything additive must default so an older
 * client parsing a newer manifest doesn't crash. See docs/RELEASE.md for the ship
 * recipe.
 */
@Serializable
data class UpdateManifest(
    @SerialName("versionCode")        val versionCode: Int,
    @SerialName("versionName")        val versionName: String,
    @SerialName("apkUrl")             val apkUrl: String,
    @SerialName("apkSha256")          val apkSha256: String,
    @SerialName("apkSizeBytes")       val apkSizeBytes: Long,
    @SerialName("changelog")          val changelog: String = "",
    @SerialName("minSupportedVersion") val minSupportedVersion: Int = 1,
    @SerialName("forceUpdate")        val forceUpdate: Boolean = false,
    @SerialName("releaseDate")        val releaseDate: String = "",
)

/**
 * Result of comparing [UpdateManifest] against the running app's [BuildConfig].
 *
 * - [UpToDate] — remote versionCode <= running versionCode. Never surface UI.
 * - [Available] — remote versionCode > running; show the update sheet. `blocking = true`
 *   when either `forceUpdate` is set OR the running versionCode falls below
 *   `minSupportedVersion`; in that state the sheet hides the Later button.
 */
sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability
    data class Available(
        val manifest: UpdateManifest,
        val blocking: Boolean,
    ) : UpdateAvailability
}
