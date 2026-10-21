package app.tandemwear.watch.ble

/**
 * How long to wait for ANCS to appear on a bonded link before giving up.
 *
 * Observed on Galaxy Watch 4 / iOS 26: right after a reconnect the first discovery can
 * return only the unencrypted services (5, no ANCS); ANCS is published once the link is
 * encrypted with the bond's keys. Retries wait progressively longer (≈30 s in total) and
 * end early when Android reports encryption or a Service Changed indication.
 */
object AncsDiscoveryPolicy {

    /** Wait before each re-discovery; the list length is the number of retries. */
    val RETRY_DELAYS_MS: List<Long> = listOf(1_500L, 3_000L, 5_000L, 8_000L, 12_000L)

    /** From this retry on, clear Android's GATT cache before re-discovering (it may be stale). */
    const val REFRESH_CACHE_FROM_RETRY = 2

    /** Delay before retry number [retry] (0-based), or null when retries are exhausted. */
    fun delayBeforeRetry(retry: Int): Long? = RETRY_DELAYS_MS.getOrNull(retry)

    fun shouldRefreshCache(retry: Int): Boolean = retry >= REFRESH_CACHE_FROM_RETRY

    enum class OnExhausted {
        /** Paired iPhone: drop the link and let the normal reconnection try again later. */
        RECONNECT_LATER,
        /** Not our paired iPhone (e.g. during pairing): report an error to the user. */
        REPORT_ERROR
    }

    fun onExhausted(isPairedPhone: Boolean): OnExhausted =
        if (isPairedPhone) OnExhausted.RECONNECT_LATER else OnExhausted.REPORT_ERROR
}
