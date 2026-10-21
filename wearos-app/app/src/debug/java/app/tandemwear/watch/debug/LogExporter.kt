package app.tandemwear.watch.debug

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import app.tandemwear.watch.diag.DiagnosticsLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only: copies the diagnostics files into the watch's Download/ folder through
 * MediaStore (no storage permission needed for files the app creates, API 29+).
 * Then: `adb pull /sdcard/Download/<name>`.
 */
object LogExporter {

    /** Returns a one-line result for the probe's log. Call off the main thread. */
    fun exportToDownloads(context: Context): String {
        val log = DiagnosticsLog.fileLog ?: return "export: no log file attached"
        if (log.files().isEmpty()) return "export: log file is empty"
        val name = "tandemwear-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return "export: could not create $name"
        return try {
            val bytes = resolver.openOutputStream(uri)?.use { log.exportTo(it) }
                ?: return "export: could not open $name"
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            "export: $bytes bytes → /sdcard/Download/$name (adb pull /sdcard/Download/$name)"
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            "export FAILED: ${e.message}"
        }
    }
}
