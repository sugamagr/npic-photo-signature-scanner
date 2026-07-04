package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * PRD §6 portal-window JPEG compressor.
 *
 * The UPMSP portal accepts files strictly in the 10 KB ≤ size ≤ 30 KB window. Below 10 KB
 * the portal rejects for "poor quality"; above 30 KB it rejects for size cap. This class
 * hits that window via a two-phase algorithm:
 *
 * ### Phase 1: binary-search on quality
 * Search JPEG quality in [30, 95] with a 10-iteration cap. On each step, encode the input
 * bitmap at the mid quality and land in one of three buckets:
 *   - size > maxBytes → search lower half (reduce quality)
 *   - size < minBytes → search upper half (increase quality)
 *   - in-window       → return immediately
 * If the search converges without hitting the window (quality gap ≤ 1 and last encode is
 * still out of window), fall through to Phase 2.
 *
 * ### Phase 2: downscale-and-retry
 * When even quality-30 is still > maxBytes (large source, tiny window), downscale the
 * bitmap by 0.85× (both dimensions) and recurse Phase 1. Cap total downscales at 6 (which
 * brings any reasonable source into range) so we never spin forever. Each downscale creates
 * a fresh intermediate bitmap; we recycle every intermediate before returning.
 *
 * ### PRD §6.1 Option A: under-min accepted with log
 * If we bottom out — quality 95 on a downscaled bitmap still produces < minBytes — the
 * result is accepted with `underMinAccepted = true` and a warning log. The Export UI shows
 * a toast so the user knows the portal MAY reject; retaking the photo is the fix. This is
 * PRD §6.1 "Option A: accept-and-log". "Option B: hard-fail" is left as a future config
 * toggle.
 *
 * ### Bitmap ownership
 * The input bitmap is NEVER recycled here — callers manage its lifecycle. Intermediate
 * downscaled bitmaps ARE recycled internally.
 */
class JpegCompressor {

    /**
     * A single compression result. [bytes] is the final encoded JPEG payload. [quality] is
     * the final JPEG quality (30–95). [downscales] is the number of 0.85× downscale steps
     * applied to reach the window. [underMinAccepted] is true when we accepted a result
     * below the min window (PRD §6.1 Option A).
     */
    data class Result(
        val bytes: ByteArray,
        val quality: Int,
        val downscales: Int,
        val underMinAccepted: Boolean,
    ) {
        val sizeBytes: Int get() = bytes.size

        // ByteArray equality is reference-based in Kotlin data classes, which is what we
        // want for an opaque result buffer — the caller compares by identity anyway.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * Compress [source] into a JPEG that fits `[minBytes, maxBytes]` when possible. See
     * class KDoc for the two-phase algorithm.
     *
     * ### Args
     * - [minBytes] defaults to 10 KB (PRD §6 window floor)
     * - [maxBytes] defaults to 30 KB (PRD §6 window ceiling)
     *
     * Never returns null. The pathological "even q95 on a heavily downscaled bitmap is
     * under min" case surfaces as `underMinAccepted = true`.
     */
    fun compress(
        source: Bitmap,
        minBytes: Int = MIN_BYTES,
        maxBytes: Int = MAX_BYTES,
    ): Result {
        require(minBytes > 0 && maxBytes > minBytes) {
            "Invalid window: min=$minBytes max=$maxBytes"
        }

        var working: Bitmap = source
        var downscales = 0

        while (true) {
            val phase1 = binarySearchQuality(working, minBytes, maxBytes)
            when (phase1.outcome) {
                SearchOutcome.InWindow -> return Result(
                    bytes = phase1.bytes,
                    quality = phase1.quality,
                    downscales = downscales,
                    underMinAccepted = false,
                ).also {
                    if (working !== source) working.recycle()
                }

                SearchOutcome.StuckAboveMax -> {
                    // Even Q30 exceeds the ceiling — downscale and try again.
                    if (downscales >= MAX_DOWNSCALES) {
                        // Give up: return the smallest we could produce even though it's
                        // still over the max. Portal will reject, but that's the caller's
                        // problem to surface. This is a very-large-photo edge case.
                        Log.w(
                            TAG,
                            "Compression exhausted downscales; returning oversize result " +
                                "size=${phase1.bytes.size} q=${phase1.quality}",
                        )
                        return Result(
                            bytes = phase1.bytes,
                            quality = phase1.quality,
                            downscales = downscales,
                            underMinAccepted = false,
                        ).also {
                            if (working !== source) working.recycle()
                        }
                    }
                    val next = downscale(working, DOWNSCALE_FACTOR)
                    if (working !== source) working.recycle()
                    working = next
                    downscales += 1
                }

                SearchOutcome.StuckBelowMin -> {
                    // Even Q95 falls below the floor — PRD §6.1 Option A. Accept with a
                    // warning; retaking the photo at a higher-resolution setting is the fix.
                    Log.w(
                        TAG,
                        "Compression under min: size=${phase1.bytes.size} q=${phase1.quality} " +
                            "downscales=$downscales — accepting per PRD §6.1 Option A",
                    )
                    return Result(
                        bytes = phase1.bytes,
                        quality = phase1.quality,
                        downscales = downscales,
                        underMinAccepted = true,
                    ).also {
                        if (working !== source) working.recycle()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private data class SearchResult(
        val bytes: ByteArray,
        val quality: Int,
        val outcome: SearchOutcome,
    )

    private enum class SearchOutcome { InWindow, StuckAboveMax, StuckBelowMin }

    /**
     * Binary-search JPEG quality within [QUALITY_MIN, QUALITY_MAX] for a size in the target
     * window. Returns the last encode alongside a bucket classification so the caller can
     * decide whether to downscale, accept-under-min, or return.
     */
    private fun binarySearchQuality(bmp: Bitmap, minBytes: Int, maxBytes: Int): SearchResult {
        var low = QUALITY_MIN
        var high = QUALITY_MAX
        var lastBytes: ByteArray = encode(bmp, high)
        var lastQuality = high

        // Fast path: max quality already fits.
        if (lastBytes.size in minBytes..maxBytes) {
            return SearchResult(lastBytes, high, SearchOutcome.InWindow)
        }
        // Fast path: even min quality is above ceiling → StuckAboveMax immediately.
        val minEncoded = encode(bmp, low)
        if (minEncoded.size > maxBytes) {
            return SearchResult(minEncoded, low, SearchOutcome.StuckAboveMax)
        }
        // Fast path: even max quality is below floor → StuckBelowMin. Note we already
        // encoded high above; reuse.
        if (lastBytes.size < minBytes) {
            return SearchResult(lastBytes, high, SearchOutcome.StuckBelowMin)
        }
        // Otherwise: search converges.
        lastBytes = minEncoded
        lastQuality = low

        var iterations = 0
        while (low <= high && iterations < SEARCH_ITERATIONS_CAP) {
            iterations += 1
            val mid = (low + high) / 2
            val encoded = encode(bmp, mid)
            lastBytes = encoded
            lastQuality = mid
            when {
                encoded.size in minBytes..maxBytes ->
                    return SearchResult(encoded, mid, SearchOutcome.InWindow)
                encoded.size > maxBytes -> high = mid - 1
                else /* encoded.size < minBytes */ -> low = mid + 1
            }
        }
        // Converged without hitting window. Decide bucket from the last encode.
        return SearchResult(
            lastBytes,
            lastQuality,
            if (lastBytes.size > maxBytes) SearchOutcome.StuckAboveMax
            else SearchOutcome.StuckBelowMin,
        )
    }

    private fun encode(bmp: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun downscale(source: Bitmap, factor: Float): Bitmap {
        val w = max(1, (source.width * factor).roundToInt())
        val h = max(1, (source.height * factor).roundToInt())
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private companion object {
        const val TAG = "JpegCompressor"

        /** PRD §6 window. */
        const val MIN_BYTES = 10 * 1024
        const val MAX_BYTES = 30 * 1024

        /** PRD §6 quality search range. */
        const val QUALITY_MIN = 30
        const val QUALITY_MAX = 95

        /** Hard cap on binary-search iterations. `log2(65) ≈ 6.02` — 10 is generous. */
        const val SEARCH_ITERATIONS_CAP = 10

        /** PRD §6 downscale step. */
        const val DOWNSCALE_FACTOR = 0.85f

        /** Cap at 6 rounds — `0.85^6 ≈ 0.377`, bringing 1600 px to ~600 px, well below
         *  any conceivable case where Q30 on a 600 px source still exceeds 30 KB. */
        const val MAX_DOWNSCALES = 6
    }
}
