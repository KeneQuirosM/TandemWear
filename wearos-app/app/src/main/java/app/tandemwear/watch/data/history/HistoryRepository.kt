package app.tandemwear.watch.data.history

import kotlinx.coroutines.flow.Flow

/**
 * Notification history kept on the watch (decision of 21-10: last [KEEP], shown [SHOW] on
 * the home screen, cleared when the iPhone is unpaired, can be turned off in Settings).
 */
class HistoryRepository(private val dao: HistoryDao) {

    fun recent(limit: Int = SHOW): Flow<List<HistoryEntry>> = dao.observeRecent(limit)

    fun entry(id: Long): Flow<HistoryEntry?> = dao.observe(id)

    fun count(): Flow<Int> = dao.observeCount()

    fun apps(): Flow<List<SeenApp>> = dao.observeApps()

    fun app(appId: String): Flow<SeenApp?> = dao.observeApp(appId)

    /** Stores [entry] (if [store]) and always remembers the app for Settings › Apps. */
    suspend fun record(entry: HistoryEntry, store: Boolean, nowMs: Long = System.currentTimeMillis()) {
        dao.upsertApp(SeenApp(entry.appId, entry.appName, entry.categoryId, nowMs))
        if (!store) return
        dao.insert(entry)
        dao.trimTo(KEEP)
    }

    /** An app whose notification was dropped (disabled) still appears in Settings › Apps. */
    suspend fun rememberApp(appId: String, appName: String, categoryId: Int, nowMs: Long = System.currentTimeMillis()) {
        dao.upsertApp(SeenApp(appId, appName, categoryId, nowMs))
    }

    /** The iPhone removed it (read or dismissed there). */
    suspend fun removedOnIphone(androidId: Int, sessionId: Long) = dao.deleteByAndroidId(androidId, sessionId)

    /** "Dismiss" on the watch. */
    suspend fun dismiss(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    companion object {
        const val KEEP = 50
        const val SHOW = 20
    }
}
