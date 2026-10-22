package app.tandemwear.watch.battery

import app.tandemwear.watch.battery.BatteryPolicy.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPolicyTest {

    @Test
    fun `subscribe only when chosen and supported`() {
        assertEquals(Mode.SUBSCRIBE, BatteryPolicy.effectiveMode(Mode.SUBSCRIBE, canNotify = true))
        assertEquals(Mode.POLL, BatteryPolicy.effectiveMode(Mode.SUBSCRIBE, canNotify = false))
        assertEquals(Mode.POLL, BatteryPolicy.effectiveMode(Mode.POLL, canNotify = true))
    }

    @Test
    fun `label`() {
        assertEquals("34%", BatteryPolicy.label(34))
        assertEquals("100%", BatteryPolicy.label(100))
        assertNull(BatteryPolicy.label(null))
        assertNull(BatteryPolicy.label(101))
    }

    @Test
    fun `poll every 15 minutes, start after AMS`() {
        assertEquals(15 * 60_000L, BatteryPolicy.POLL_INTERVAL_MS)
        assert(BatteryPolicy.START_DELAY_MS > 5_000L)
    }
}
