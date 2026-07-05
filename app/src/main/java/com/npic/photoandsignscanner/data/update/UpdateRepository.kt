package com.npic.photoandsignscanner.data.update

import android.util.Log
import com.npic.photoandsignscanner.BuildConfig
import com.npic.photoandsignscanner.domain.model.UpdateAvailability
import com.npic.photoandsignscanner.domain.model.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * m2508: fetches the update manifest from a stable HTTPS URL and decides whether the
 * running APK is out of date.
 *
 * The manifest lives at `raw.githubusercontent.com/{owner}/{repo}/main/version.json`
 * because raw.githubusercontent.com has no per-IP rate limit (unlike api.github.com's
 * 60 req/hour unauthenticated cap — a 50-teacher school on shared Wi-Fi would blow
 * through that on Monday morning).
 *
 * Blocking IO wrapped in `withContext(Dispatchers.IO)` matches the repository pattern
 * used everywhere else in this codebase (RoomStudentRepository, SettingsViewModel).
 * No OkHttp or Ktor dependency added — the payload is <1 KB and HttpURLConnection is
 * built into the JDK.
 */
class UpdateRepository(
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * Returns [UpdateAvailability.Available] when the remote manifest advertises a
     * higher versionCode than the running app; [UpdateAvailability.UpToDate] otherwise
     * or on any network / parse failure. We swallow errors here rather than surface
     * them because the user should not see "Update check failed" toasts on every
     * cold launch — a silent no-op retries next launch, which is the right default
     * for a check-on-launch policy.
     */
    suspend fun check(): UpdateAvailability = withContext(Dispatchers.IO) {
        val manifest = runCatching { fetchManifest() }.getOrElse { throwable ->
            Log.w(TAG, "Manifest fetch failed: ${throwable.message}")
            return@withContext UpdateAvailability.UpToDate
        }
        val installed = BuildConfig.VERSION_CODE
        val remote = manifest.versionCode
        if (remote <= installed) {
            return@withContext UpdateAvailability.UpToDate
        }
        val blocking = manifest.forceUpdate || installed < manifest.minSupportedVersion
        UpdateAvailability.Available(manifest = manifest, blocking = blocking)
    }

    private fun fetchManifest(): UpdateManifest {
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NPIC-Scanner/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $manifestUrl")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString(UpdateManifest.serializer(), body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "UpdateRepository"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        // m2508: repo owner/name hardcoded because a config value the user could
        // change would let a malicious actor point the check at a poisoned manifest
        // that ships an APK signed with a different key — install would fail, but
        // the download itself would burn data.
        const val DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/sugamagr/npic-photo-signature-scanner/main/version.json"
    }
}
