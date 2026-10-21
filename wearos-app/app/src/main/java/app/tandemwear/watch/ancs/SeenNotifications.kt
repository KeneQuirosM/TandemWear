package app.tandemwear.watch.ancs

import java.io.File
import java.security.MessageDigest

/**
 * Fingerprints of notifications already shown, so one the iPhone re-sends after a restart
 * (with a new UID) is recognised. Only a SHA-256 of (app, title, text, date) is kept — never
 * the content — in a small text file ("<hex> <epochMs>" per line), for [RETENTION_MS] and at
 * most [MAX_ENTRIES]. Notifications without a date are never fingerprinted (can't tell a
 * repeat from a new identical message). Pure Java I/O, unit-tested.
 */
class SeenNotifications(private val file: File) {

    private val entries = LinkedHashMap<String, Long>()
    private var loaded = false

    @Synchronized
    fun contains(fingerprint: String, nowMs: Long): Boolean {
        load(nowMs)
        return entries.containsKey(fingerprint)
    }

    @Synchronized
    fun add(fingerprint: String, nowMs: Long) {
        load(nowMs)
        if (entries.containsKey(fingerprint)) return
        entries[fingerprint] = nowMs
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        save()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        loaded = true
        file.delete()
    }

    private fun load(nowMs: Long) {
        if (!loaded) {
            loaded = true
            try {
                if (file.exists()) file.readLines().forEach { line ->
                    val parts = line.split(' ')
                    val time = parts.getOrNull(1)?.toLongOrNull()
                    if (parts.size == 2 && parts[0].length == 64 && time != null) entries[parts[0]] = time
                }
            } catch (e: Exception) {
                entries.clear()
            }
        }
        val before = entries.size
        entries.entries.removeAll { nowMs - it.value > RETENTION_MS }
        if (entries.size != before) save()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(entries.entries.joinToString("") { "${it.key} ${it.value}\n" })
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            // Losing the list only means an old notification may alert again
        }
    }

    companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60_000
        const val MAX_ENTRIES = 2_000

        /** Null when there is no date: such notifications are always treated as new. */
        fun fingerprint(appId: String, title: String, message: String, dateText: String?): String? {
            if (dateText.isNullOrBlank()) return null
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(listOf(appId, title, message, dateText.trim()).joinToString("\u0000").toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
