package app.tandemwear.watch.data.settings

import app.tandemwear.watch.notify.Conversation

/** Per-app choices. The default (all false) means "show normally". */
data class AppRule(
    /** Never show notifications from this app on the watch. */
    val disabled: Boolean = false,
    /** Show them without sound or vibration ("Silenciar esta app"). */
    val muted: Boolean = false,
    /** Show only "New notification from <app>", never the text. */
    val hideContent: Boolean = false
) {
    val isDefault: Boolean get() = this == AppRule()
}

/**
 * Do-not-disturb window in minutes since midnight (local time). [startMinute] == [endMinute]
 * means an empty window. A window may cross midnight (22:00 → 07:00).
 */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    /** Calls still ring during the window. */
    val allowCalls: Boolean = true
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute $startMinute" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute $endMinute" }
    }

    /** True when [minuteOfDay] falls inside the window (start inclusive, end exclusive). */
    fun isActiveAt(minuteOfDay: Int): Boolean {
        if (!enabled || startMinute == endMinute) return false
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        return if (startMinute < endMinute) {
            m >= startMinute && m < endMinute
        } else {
            m >= startMinute || m < endMinute
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** Everything the user can change in Settings. Immutable snapshot. */
data class TandemSettings(
    val appRules: Map<String, AppRule> = emptyMap(),
    val quietHours: QuietHours = QuietHours(),
    /** Keep a history of received notifications on the watch. */
    val historyEnabled: Boolean = true,
    /** Hide notification text on the watch's lock screen (all apps). */
    val hideContentOnLockScreen: Boolean = false,
    /** Music from the iPhone (AMS): player, home card and Tile card. */
    val musicEnabled: Boolean = true,
    /** VIP conversations: they ring even when their app is muted. */
    val vips: List<Conversation> = emptyList(),
    /** VIPs also ring during our quiet hours (not the system's Do Not Disturb). */
    val vipRingsInQuietHours: Boolean = false,
    /** Silenced conversations and when the silence ends (ms since epoch). */
    val mutedConversations: Map<Conversation, Long> = emptyMap()
) {
    fun ruleFor(appId: String): AppRule = appRules[appId] ?: AppRule()

    fun isVip(appId: String, title: String): Boolean = vips.any { it.matches(appId, title) }

    /** End of the conversation's silence if it is still silenced at [nowMs]. */
    fun mutedUntil(appId: String, title: String, nowMs: Long): Long? =
        mutedConversations.entries.firstOrNull { (c, until) -> until > nowMs && c.matches(appId, title) }?.value
}
