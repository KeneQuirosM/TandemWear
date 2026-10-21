package app.tandemwear.watch.ancs

/**
 * Maps ANCS NotificationUIDs to Android notification ids, per ANCS session.
 *
 * ANCS UIDs are only valid within a session (Apple spec): after every reconnect the iPhone
 * renumbers — existing notifications get 0..N, new ones continue from there. Deriving the
 * Android id from the UID therefore let a new notification silently replace an old one
 * still on the watch (no alert), and a REMOVED event cancel the wrong notification.
 *
 * Here every (session, UID) pair gets a fresh Android id from a monotonic counter that is
 * persisted through [onNextIdChanged], so ids are never reused across sessions or process
 * restarts (until the counter wraps after ~1e9 notifications).
 *
 * Not thread-safe: use from one thread (the service's main thread).
 */
class NotificationIdRegistry(
    initialNextId: Int = FIRST_ID,
    private val onNextIdChanged: (Int) -> Unit = {}
) {

    companion object {
        /** Ids below this are reserved (service notification, call notification…). */
        const val FIRST_ID = 1000
        const val LAST_ID = 999_999_999
        const val NO_SESSION = 0L
    }

    private var nextId = initialNextId.coerceIn(FIRST_ID, LAST_ID)
    private val idsByUid = HashMap<Long, Int>()

    /** Token of the current ANCS session, or [NO_SESSION]. */
    var sessionId: Long = NO_SESSION
        private set

    val isSessionActive: Boolean get() = sessionId != NO_SESSION

    /** Number of UIDs mapped in the current session. */
    val mappedCount: Int get() = idsByUid.size

    /** A new ANCS session began: forget every UID from the previous one. */
    fun startSession(token: Long) {
        require(token != NO_SESSION) { "Session token must be non-zero" }
        idsByUid.clear()
        sessionId = token
    }

    /** The session ended (disconnect): UIDs are meaningless from now on. */
    fun endSession() {
        idsByUid.clear()
        sessionId = NO_SESSION
    }

    fun isCurrent(token: Long): Boolean = token != NO_SESSION && token == sessionId

    /** Android id for [uid] in this session, allocating a fresh one the first time. */
    fun idFor(uid: Long): Int = idsByUid.getOrPut(uid) { allocate() }

    /** Android id for [uid] if it was posted in this session. */
    fun existingId(uid: Long): Int? = idsByUid[uid]

    /** Forget [uid] (REMOVED event) and return its Android id, if any. */
    fun remove(uid: Long): Int? = idsByUid.remove(uid)

    private fun allocate(): Int {
        val id = nextId
        nextId = if (nextId >= LAST_ID) FIRST_ID else nextId + 1
        onNextIdChanged(nextId)
        return id
    }
}
