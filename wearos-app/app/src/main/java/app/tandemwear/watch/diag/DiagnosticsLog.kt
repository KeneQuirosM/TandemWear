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
 * [TAG]. The debug ANCS probe screen displays it; `adb logcat -s TandemWear:I` shows it
 * from a computer. Keeps the last [capacity] lines. Thread-safe.
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

    /** Set once from the Application: whether content may be logged. */
    @Volatile
    var allowContent = false

    fun log(message: String) {
        Log.i(TAG, message)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _lines.update { (it + "$time $message").takeLast(capacity) }
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

    internal fun setCapacityForTest(value: Int) {
        capacity = value
    }
}
