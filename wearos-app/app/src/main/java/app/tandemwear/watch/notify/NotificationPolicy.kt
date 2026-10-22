package app.tandemwear.watch.notify

import app.tandemwear.watch.data.settings.TandemSettings

/**
 * Decides how a notification from the iPhone is shown, from the user's settings only.
 * Pure: no Android types, so every rule is covered by unit tests. AncsService applies the
 * decision when it builds the Android notification.
 */
object NotificationPolicy {

    enum class Alert {
        /** Normal: channel sound/vibration and heads-up. */
        ALERT,
        /** Posted silently: no sound, vibration or heads-up (muted app or quiet hours). */
        SILENT
    }

    data class Decision(
        /** False = drop it entirely (app disabled). Nothing is posted or stored. */
        val show: Boolean,
        val alert: Alert,
        /** Replace title and text with a generic "New notification from <app>". */
        val hideContent: Boolean,
        /** Hide the text on the lock screen (public version with no content). */
        val privateOnLockScreen: Boolean
    )

    private val DROP = Decision(show = false, alert = Alert.SILENT, hideContent = true, privateOnLockScreen = true)

    /**
     * Order: a disabled app shows nothing (VIP included); a silenced conversation is silent;
     * a VIP rings even if its app is muted, and during our quiet hours only when
     * [TandemSettings.vipRingsInQuietHours] is on; otherwise app mute and quiet hours apply.
     * [title] and [nowMs] identify the conversation and the time for VIP / silence.
     */
    fun decide(
        settings: TandemSettings,
        appId: String,
        minuteOfDay: Int,
        title: String = "",
        nowMs: Long = 0L
    ): Decision {
        val rule = settings.ruleFor(appId)
        if (rule.disabled) return DROP
        val quiet = settings.quietHours.isActiveAt(minuteOfDay)
        val alert = when {
            title.isNotBlank() && settings.mutedUntil(appId, title, nowMs) != null -> Alert.SILENT
            title.isNotBlank() && settings.isVip(appId, title) ->
                if (quiet && !settings.vipRingsInQuietHours) Alert.SILENT else Alert.ALERT
            rule.muted || quiet -> Alert.SILENT
            else -> Alert.ALERT
        }
        return Decision(
            show = true,
            alert = alert,
            hideContent = rule.hideContent,
            privateOnLockScreen = rule.hideContent || settings.hideContentOnLockScreen
        )
    }

    /**
     * Incoming calls: the call screen rings unless quiet hours are on without the calls
     * exception. Disabling or muting the Phone app does not silence calls (they have their
     * own setting through quiet hours), so a call is never missed by accident.
     */
    fun shouldRingForCall(settings: TandemSettings, minuteOfDay: Int): Boolean {
        val quiet = settings.quietHours
        return !quiet.isActiveAt(minuteOfDay) || quiet.allowCalls
    }
}
