package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.util.Log
import com.npic.photoandsignscanner.app.NpicApplication
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * Single place the app crosses the Android bitmap ↔ OpenCV `Mat` boundary.
 *
 * Every detector, filter, and renderer that needs Mat routes through here so we control:
 * 1. The one-time `System.loadLibrary("opencv_java4")` gate (via [NpicApplication.initOpenCVOnce]).
 * 2. Bitmap config normalization — OpenCV expects [Bitmap.Config.ARGB_8888]; anything else is
 *    silently converted before conversion so callers never see a "wrong format" runtime crash.
 * 3. Deterministic cleanup — Mats hold native buffers and must be `release()`-ed. The
 *    [use] helper mirrors [kotlin.io.use] semantics for that.
 */
class OpenCvBridge {

    /** True if OpenCV's native library is loaded and ready for use. Cheap; safe to call often. */
    fun isAvailable(): Boolean = NpicApplication.initOpenCVOnce()

    /**
     * Convert a bitmap to an RGBA Mat. Returns null if OpenCV isn't loaded — callers MUST
     * handle this by falling back to their skip-path so we never crash the Edit screen when
     * the native lib is missing (e.g. debug build on an unsupported ABI emulator).
     */
    fun toMat(bitmap: Bitmap): Mat? {
        if (!isAvailable()) return null
        val normalized = bitmap.ensureArgb8888()
        return Mat().also { mat ->
            try {
                Utils.bitmapToMat(normalized, mat)
            } catch (t: Throwable) {
                Log.e(TAG, "bitmapToMat failed", t)
                mat.release()
                return null
            }
        }
    }

    /**
     * Convert a Mat back to a bitmap. Caller owns [target]; on failure the mat is released
     * but the target bitmap is left untouched.
     */
    fun toBitmap(mat: Mat, target: Bitmap): Boolean {
        if (!isAvailable()) return false
        return try {
            Utils.matToBitmap(mat, target)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "matToBitmap failed", t)
            false
        }
    }

    /**
     * Run [block] with a mat backed by [bitmap], releasing the mat afterwards regardless of
     * outcome. Returns null when OpenCV isn't available (caller's skip path).
     *
     * Mirrors [kotlin.io.use] for `Mat`, since [Mat] is `Closeable`-shaped but predates
     * `AutoCloseable` on the version we ship.
     */
    inline fun <T> withMat(bitmap: Bitmap, block: (Mat) -> T): T? {
        val mat = toMat(bitmap) ?: return null
        return try {
            block(mat)
        } finally {
            mat.release()
        }
    }

    private fun Bitmap.ensureArgb8888(): Bitmap =
        if (config == Bitmap.Config.ARGB_8888) this
        else copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false)

    private companion object {
        const val TAG = "OpenCvBridge"
    }
}
