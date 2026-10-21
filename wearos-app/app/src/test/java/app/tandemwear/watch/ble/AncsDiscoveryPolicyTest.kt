package app.tandemwear.watch.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AncsDiscoveryPolicyTest {

    @Test
    fun `retries are patient and increasing`() {
        val delays = generateSequence(0) { it + 1 }
            .map { AncsDiscoveryPolicy.delayBeforeRetry(it) }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
        assertTrue("at least 4 retries", delays.size >= 4)
        assertTrue("each wait longer than the previous", delays.zipWithNext().all { (a, b) -> b > a })
        assertTrue("about 30 s in total", delays.sum() >= 25_000L)
        // The old behaviour gave up after two 2 s retries
        assertTrue(delays.sum() > 4_000L)
    }

    @Test
    fun `exhausted after the last retry`() {
        assertNull(AncsDiscoveryPolicy.delayBeforeRetry(AncsDiscoveryPolicy.RETRY_DELAYS_MS.size))
    }

    @Test
    fun `gatt cache is refreshed only on later retries`() {
        assertFalse(AncsDiscoveryPolicy.shouldRefreshCache(0))
        assertFalse(AncsDiscoveryPolicy.shouldRefreshCache(1))
        assertTrue(AncsDiscoveryPolicy.shouldRefreshCache(2))
    }

    @Test
    fun `paired iphone is never a terminal error`() {
        assertEquals(AncsDiscoveryPolicy.OnExhausted.RECONNECT_LATER, AncsDiscoveryPolicy.onExhausted(isPairedPhone = true))
        assertEquals(AncsDiscoveryPolicy.OnExhausted.REPORT_ERROR, AncsDiscoveryPolicy.onExhausted(isPairedPhone = false))
    }

    @Test
    fun `linked without ancs is shown only after 5 s`() {
        // Night of 20-21 Oct: 1.2 s and 2.8 s → still "connecting"; 30 s → linked, no ANCS
        assertEquals(false, AncsDiscoveryPolicy.showLinkedWithoutAncs(1_211))
        assertEquals(false, AncsDiscoveryPolicy.showLinkedWithoutAncs(2_788))
        assertEquals(true, AncsDiscoveryPolicy.showLinkedWithoutAncs(5_000))
        assertEquals(true, AncsDiscoveryPolicy.showLinkedWithoutAncs(30_938))
    }
}
