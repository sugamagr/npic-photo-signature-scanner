package com.npic.photoandsignscanner.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Fires the Android Share Sheet with one or more JPEG files. Layer 8c.2 ships a stub that
 * reuses the record's on-disk `photoPath` / `signaturePath` verbatim — the real compressed
 * exports (10–30 KB portal-window JPEGs, PRD §6) land with the Save-render / Export-render
 * layer. That layer will write to `filesDir/exports/` and delete after share settles.
 *
 * `AUTHORITY` matches the FileProvider declared in AndroidManifest.xml — do not diverge.
 */
object FileShareLauncher {

    private const val TAG = "FileShareLauncher"
    const val MIME_JPEG = "image/jpeg"
    const val MIME_ZIP = "application/zip"

    /** Fire the share sheet for a single record via ACTION_SEND. */
    fun shareSingle(context: Context, filePath: String, chooserTitle: String, mimeType: String = MIME_JPEG) {
        val uri = fileUri(context, filePath) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, intent, chooserTitle)
    }

    /** Fire the share sheet for multiple records via ACTION_SEND_MULTIPLE. */
    fun shareMulti(context: Context, filePaths: List<String>, chooserTitle: String, mimeType: String = MIME_JPEG) {
        val uris = filePaths.mapNotNull { fileUri(context, it) }
        if (uris.isEmpty()) {
            Log.w(TAG, "No shareable files resolved from ${filePaths.size} paths")
            return
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, intent, chooserTitle)
    }

    private fun launch(context: Context, intent: Intent, chooserTitle: String) {
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch share chooser", t)
        }
    }

    /**
     * Resolves a file path to a shareable content:// URI via the app FileProvider. Only
     * paths inside `cacheDir` or `filesDir` are eligible; anything outside returns null and
     * logs a warning so we can't accidentally share arbitrary paths.
     */
    private fun fileUri(context: Context, filePath: String): Uri? {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "Share source missing: $filePath")
            return null
        }
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (t: IllegalArgumentException) {
            Log.e(TAG, "Path is not covered by file_provider_paths.xml: $filePath", t)
            null
        }
    }
}
