package app.tandemwear.watch.ancs

/**
 * How the incoming-call screen is brought up. Pure, unit-tested.
 *
 * The CallStyle notification's full-screen intent is the sanctioned path (Android 14+ needs
 * the USE_FULL_SCREEN_INTENT app-op). Wear OS does not always honour it (gotcha 5), so a
 * direct activity start remains as fallback: at once when full-screen is not allowed, or
 * when the screen is still not visible [FULL_SCREEN_GRACE_MS] after posting.
 */
object CallLaunchPolicy {

    const val FULL_SCREEN_GRACE_MS = 1_500L

    enum class Start {
        /** Post the notification and let its full-screen intent open the screen; check later. */
        FULL_SCREEN_THEN_CHECK,
        /** Full-screen intents are refused: start the activity directly right away. */
        DIRECT_NOW
    }

    fun initial(fullScreenAllowed: Boolean): Start =
        if (fullScreenAllowed) Start.FULL_SCREEN_THEN_CHECK else Start.DIRECT_NOW

    /** After the grace period: start directly only if the same call still rings and no screen shows. */
    fun needsFallback(sameCallStillRinging: Boolean, screenVisible: Boolean): Boolean =
        sameCallStillRinging && !screenVisible
}
