package com.npic.photoandsignscanner.data.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.npic.photoandsignscanner.domain.model.RectI
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pre-crops a captured JPEG to its on-screen guide-box region and overwrites the file.
 *
 * User directive m2228 A + B: after shutter, the raw JPEG should already be cropped to
 * what the user framed inside the guide-box (3:4 for Photo mode, 3:1 for Signature mode).
 * The Edit screen then opens with its crop overlay at the actual full-image corners of the
 * pre-cropped file — no more small quad floating inside the raw JPEG.
 *
 * ### Contract
 *
 * - Input: an absolute path to a JPEG written by [NpicCameraController.takePicture], plus a
 *   [RectI] in EXIF-corrected image-space coordinates (matching the convention used by
 *   [PreviewGuide.toImageSpace] downstream of the CameraViewModel EXIF-swap).
 * - Output: `true` on success — the file at [path] has been atomically rewritten to hold
 *   only the guide-box region as a q=95 JPEG with EXIF orientation reset to
 *   `ORIENTATION_NORMAL` (so downstream decoders don't double-rotate).
 * - Failure: `false` on any throw. Caller (CameraViewModel) must fall back to keeping the
 *   raw JPEG and passing the original `guideBoxImageSpace` through so Edit still opens at
 *   the guide-box region — just via the pre-m2228 seeding path.
 *
 * ### Why q=95 not q=92
 *
 * SourceStore writes its long-side-1600 q=92 rendering at Save time. This pre-crop stage
 * sits earlier in the pipeline and must not compound compression artifacts. q=95 gives
 * ~2 KB extra JPEG per photo but keeps the source usable across an unbounded number of
 * subsequent EditRenderer passes.
 *
 * ### Bitmap ownership
 *
 * Every intermediate bitmap is recycled before return. Never leaks a decoded source, a
 * rotated intermediate, or the crop result. The output stream is closed via `use`.
 */
class GuideBoxCropper {

    suspend fun cropJpegToGuideBox(path: String, guideBoxImageSpace: RectI): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = BitmapFactory.decodeFile(path) ?: return@runCatching false
                val rotated = applyExifOrientation(raw, path)
                if (rotated !== raw) raw.recycle()

                // Clamp the rect to the rotated bitmap bounds. The projection math can
                // land 1-2 px outside the buffer on FILL_CENTER inverse edge cases.
                val left = guideBoxImageSpace.left.coerceIn(0, rotated.width - 1)
                val top = guideBoxImageSpace.top.coerceIn(0, rotated.height - 1)
                val width = (guideBoxImageSpace.right - guideBoxImageSpace.left)
                    .coerceIn(1, rotated.width - left)
                val height = (guideBoxImageSpace.bottom - guideBoxImageSpace.top)
                    .coerceIn(1, rotated.height - top)

                val cropped = Bitmap.createBitmap(rotated, left, top, width, height)
                if (cropped !== rotated) rotated.recycle()

                FileOutputStream(File(path)).use { stream ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                }
                cropped.recycle()

                // Reset EXIF orientation to NORMAL so downstream decoders (EditViewModel
                // .applyExifOrientation, Coil in SaveSheet PreviewStrip) don't double-rotate
                // a bitmap that's already upright.
                runCatching {
                    val exif = ExifInterface(path)
                    exif.setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString(),
                    )
                    exif.saveAttributes()
                }.onFailure {
                    Log.w(TAG, "Failed to reset EXIF orientation on $path: ${it.message}")
                }

                true
            }.getOrElse {
                Log.e(TAG, "GuideBoxCropper failed for $path: ${it.message}", it)
                false
            }
        }

    /**
     * Applies EXIF orientation into pixel-space so the returned bitmap is visually upright
     * even after downstream decoders strip the tag. Mirrors the helper in EditViewModel to
     * keep the two decoding paths symmetric; extracted into a shared utility would create
     * a circular data ↔ features dep, so we duplicate ~30 lines instead.
     */
    private fun applyExifOrientation(source: Bitmap, path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) return source

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return source
        }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrDefault(source)
    }

    private companion object {
        const val TAG = "GuideBoxCropper"
        const val JPEG_QUALITY = 95
    }
}
