package app.tandemwear.watch.calls

import app.tandemwear.watch.notify.Conversation

/** How a call ended, as the watch saw it. Stored by name in the `calls` table. */
enum class CallOutcome {
    /** Answered on the iPhone (ANCS MODIFIED on the ringing call). */
    ANSWERED,
    /** Answered from the watch (ANCS positive action). */
    ANSWERED_ON_WATCH,
    /** Declined from the watch (ANCS negative action). */
    DECLINED_ON_WATCH,
    /** Ended without being answered, or an iPhone "Missed Call" notification. */
    MISSED;

    companion object {
        fun fromName(name: String): CallOutcome = entries.firstOrNull { it.name == name } ?: MISSED
    }
}

/** Rules of the call log (Phase 5.4). Pure, unit-tested. */
object CallLogPolicy {

    /** A Missed Call notification this close to a logged call of the same caller is that call. */
    const val SAME_CALL_WINDOW_MS = 3 * 60_000L

    /** Keep the last calls only. */
    const val KEEP = 100

    /** The call stopped ringing (REMOVED or session ended) with [outcome] so far. */
    fun outcomeOnEnd(outcome: CallOutcome?): CallOutcome = outcome ?: CallOutcome.MISSED

    /**
     * A "Missed Call" notification from the iPhone: true if it repeats a call already logged
     * (same caller, ignoring case and accents, within [SAME_CALL_WINDOW_MS]).
     */
    fun isAlreadyLogged(caller: String, atMs: Long, recent: List<Pair<String, Long>>): Boolean {
        val name = Conversation.normalize(caller)
        return recent.any { (c, t) -> Conversation.normalize(c) == name && kotlin.math.abs(atMs - t) <= SAME_CALL_WINDOW_MS }
    }
}
