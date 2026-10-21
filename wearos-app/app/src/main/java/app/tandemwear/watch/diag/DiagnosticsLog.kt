package app.tandemwear.watch.diag

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small in-memory log of pairing/connection milestones, mirrored to logcat with tag
 * [TAG] and, once [attachFile] is called, to a rotating file on the watch
 * (files/diag/tandemwear.log, ≈2 MB max) so a whole night can be read back. The debug ANCS
 * probe screen displays it; `adb logcat -s TandemWear:I` shows it live. Keeps the last
 * [capacity] lines in memory. Thread-safe.
 *
 * Never put notification content here except through [notificationPreview], which only
 * includes text when the build is debuggable.
 */
object DiagnosticsLog {

    const val TAG = "TandemWear"
    private const val DEFAULT_CAPACITY = 200

    private var capacity = DEFAULT_CAPACITY
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Volatile
    private var file: RotatingFileLog? = null

    /** Rotating file the log is persisted to (null until the Application attaches it). */
    val fileLog: RotatingFileLog? get() = file

    /** Called once from the Application. */
    fun attachFile(log: RotatingFileLog) {
        file = log
    }

    /** Set once from the Application: whether content may be logged. */
    @Volatile
    var allowContent = false

    fun log(message: String) {
        Log.i(TAG, message)
        val now = Date()
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
        _lines.update { (it + "$time $message").takeLast(capacity) }
        // Full date and zone offset in the file: a night spans two days and DST changes
        file?.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(now) + " " + message)
    }

    /** Notification summary for logs: text only in debuggable builds, lengths otherwise. */
    fun notificationPreview(app: String, title: String, message: String): String =
        if (allowContent) {
            "app=$app title=\"$title\" message=\"${message.take(60)}\""
        } else {
            "app=$app title(len)=${title.length} message(len)=${message.length}"
        }

    fun clear() {
        _lines.value = emptyList()
    }

    internal fun detachFileForTest() {
        file = null
    }

    internal fun setCapacityForTest(value: Int) {
        capacity = value
    }
}
