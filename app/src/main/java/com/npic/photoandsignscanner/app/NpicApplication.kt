package com.npic.photoandsignscanner.app

import android.app.Application
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application entry point.
 *
 * Kept minimal by design. Anything that would normally live here (DI graph, logging config,
 * WorkManager configuration) is added in the layer that actually needs it — not preemptively.
 *
 * OpenCV native loading is lazy: [initOpenCVOnce] is called by the Camera feature on first
 * screen entry, not from [onCreate]. This keeps app cold-start ≤ 1.5 s (PRD §2 success
 * criterion) — the OpenCV .so is 8 MB and would add ~200 ms to startup otherwise.
 */
class NpicApplication : Application() {

    companion object {
        private const val TAG = "NpicApplication"
        private val openCvLoaded = AtomicBoolean(false)

        /**
         * Loads the bundled OpenCV native library exactly once. Safe to call from any thread
         * and from any number of call sites — subsequent calls are no-ops after the first
         * successful load. Returns `true` if OpenCV is usable, `false` if the load failed
         * (which only happens on unsupported ABIs; we ship arm64-v8a and armeabi-v7a).
         *
         * The Camera feature calls this on first mount; the Edit-tier consumers assume it
         * has returned true before invocation. Current consumers: EditRenderer's
         * `applyPerspectiveCrop` (warpPerspective + getPerspectiveTransform) + `applyStraighten`
         * (warpAffine + getRotationMatrix2D), plus BitmapAdjustments' Sharpness slider
         * (GaussianBlur + addWeighted) and BitmapFilters' Document B&W preset (adaptiveThreshold).
         * PRD §7.1 / §7.2 auto edge / ink detection was removed per m2154; §7.3 crop commit stays.
         */
        fun initOpenCVOnce(): Boolean {
            if (openCvLoaded.get()) return true
            return try {
                System.loadLibrary("opencv_java4")
                openCvLoaded.set(true)
                Log.i(TAG, "OpenCV native library loaded")
                true
            } catch (t: UnsatisfiedLinkError) {
                Log.e(TAG, "OpenCV native library failed to load", t)
                false
            }
        }
    }
}
