package app.tandemwear.watch.diag

import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Append-only text log in [dir] that survives logcat's small ring buffer, so a whole night
 * can be read later. When [baseName] would exceed [maxBytes] it rotates:
 * `name` → `name.1` → … → `name.[backups]` (the oldest is deleted). Total size stays below
 * (backups + 1) × maxBytes. Writes run on [executor] (one background thread by default),
 * never on the caller's thread. Pure Java I/O: tested on the JVM.
 */
class RotatingFileLog(
    private val dir: File,
    private val baseName: String = DEFAULT_NAME,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val backups: Int = DEFAULT_BACKUPS,
    private val executor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TandemWear-diag-file").apply { isDaemon = true }
    }
) {
    init {
        require(maxBytes > 0) { "maxBytes" }
        require(backups >= 1) { "backups" }
    }

    private val lock = Any()
    private val current: File get() = File(dir, baseName)
    private fun backup(index: Int) = File(dir, "$baseName.$index")

    /** Queue one line (a newline is added). */
    fun append(line: String) {
        executor.execute { appendNow(line) }
    }

    private fun appendNow(line: String) = synchronized(lock) {
        try {
            dir.mkdirs()
            val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
            if (current.exists() && current.length() + bytes.size > maxBytes) rotate()
            current.appendBytes(bytes)
        } catch (e: Exception) {
            // Diagnostics must never break the app (disk full, etc.)
        }
    }

    private fun rotate() {
        backup(backups).delete()
        for (i in backups - 1 downTo 1) {
            val from = backup(i)
            if (from.exists()) from.renameTo(backup(i + 1))
        }
        current.renameTo(backup(1))
    }

    /** Existing log files, oldest first. */
    fun files(): List<File> = synchronized(lock) {
        ((backups downTo 1).map { backup(it) } + current).filter { it.exists() }
    }

    /** Copies every file, oldest first, into [out] (one continuous text). Returns bytes written. */
    fun exportTo(out: OutputStream): Long = synchronized(lock) {
        var total = 0L
        for (file in files()) {
            file.inputStream().use { total += it.copyTo(out) }
        }
        out.flush()
        total
    }

    companion object {
        const val DEFAULT_NAME = "tandemwear.log"
        const val DEFAULT_MAX_BYTES = 512L * 1024
        const val DEFAULT_BACKUPS = 3
        /** Directory under the app's files dir; `run-as … cat files/diag/tandemwear.log`. */
        const val DIR_NAME = "diag"
    }
}
