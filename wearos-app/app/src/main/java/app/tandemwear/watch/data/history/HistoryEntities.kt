package app.tandemwear.watch.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One notification shown on the watch. [sessionId] + [uid] identify it on the iPhone only
 * while that ANCS session lasts (UIDs are renumbered on reconnect); actions are offered only
 * for the current session.
 */
@Entity(tableName = "history", indices = [Index("androidId"), Index("postedAt")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Android notification id (NotificationIdRegistry). */
    val androidId: Int,
    val sessionId: Long,
    val uid: Long,
    val appId: String,
    val appName: String,
    val categoryId: Int,
    val title: String,
    val message: String,
    /** When the iPhone says it was posted (ms since epoch). */
    val postedAt: Long,
    val positiveLabel: String?,
    val negativeLabel: String?,
    /** "Hide message text" was on for this app: [title]/[message] hold no content. */
    val contentHidden: Boolean = false
)

/** Every app that has sent a notification, for Settings › Apps. */
@Entity(tableName = "seen_apps")
data class SeenApp(
    @PrimaryKey val appId: String,
    val displayName: String,
    val categoryId: Int,
    val lastSeen: Long
)
