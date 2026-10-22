package app.tandemwear.watch.battery

/**
 * How the iPhone's battery level is followed (Phase 5.2). Pure, unit-tested.
 *
 * SUBSCRIBE: one read, then notifications on change (one CCCD write per session).
 * POLL: one read every [POLL_INTERVAL_MS], no CCCD write — for when the subscription write
 * buzzes the watch (gotcha 10). The mode is a debug switch in TW Probe ANCS.
 */
object BatteryPolicy {

    enum class Mode { SUBSCRIBE, POLL }

    /** After SESSION ACTIVE, and after AMS's own start (5 s). */
    const val START_DELAY_MS = 8_000L

    /** POLL mode. Coroutine delay: may stretch in Doze (gotcha 23), harmless for a battery level. */
    const val POLL_INTERVAL_MS = 15 * 60_000L

    /** Give up one step if ANCS stays busy this long; the next session or poll tries again. */
    const val MAX_WAIT_MS = 30_000L

    /** SUBSCRIBE only when chosen and the characteristic can notify; otherwise POLL. */
    fun effectiveMode(chosen: Mode, canNotify: Boolean): Mode =
        if (chosen == Mode.SUBSCRIBE && canNotify) Mode.SUBSCRIBE else Mode.POLL

    /** Short text for Home, the Tile and the complication: "34%", or null without a level. */
    fun label(level: Int?): String? = level?.takeIf { it in 0..100 }?.let { "$it%" }
}
