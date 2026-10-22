package app.tandemwear.watch.iphonetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CurrentTimeProtocolTest {

    // 2026-10-22 21:30:15, Thursday (4), fractions 128 (0.5 s), adjust reason: zone change
    private val ct = byteArrayOf(0xEA.toByte(), 0x07, 10, 22, 21, 30, 15, 4, 128.toByte(), 0x04)

    @Test
    fun `current time decodes local time and adjust reason`() {
        val t = CurrentTimeProtocol.parseCurrentTime(ct)!!
        assertEquals(LocalDateTime.of(2026, 10, 22, 21, 30, 15, 500_000_000), t.local)
        assertEquals(listOf("zone change"), t.reasons)
        assertNull(CurrentTimeProtocol.parseCurrentTime(byteArrayOf(1, 2, 3)))
        // Month 13 is not a date
        assertNull(CurrentTimeProtocol.parseCurrentTime(byteArrayOf(0xEA.toByte(), 0x07, 13, 1, 0, 0, 0, 1, 0, 0)))
    }

    @Test
    fun `local time information gives the UTC offset`() {
        // Costa Rica: UTC-6 = -24 quarters, no DST
        val cr = CurrentTimeProtocol.parseLocalTimeInfo(byteArrayOf(-24, 0))!!
        assertEquals(ZoneOffset.ofHours(-6), cr.offset)
        // Madrid in summer: UTC+1 (+4) and DST +1 h (4)
        assertEquals(ZoneOffset.ofHours(2), CurrentTimeProtocol.parseLocalTimeInfo(byteArrayOf(4, 4))!!.offset)
        // Unknown zone or DST: no offset
        assertNull(CurrentTimeProtocol.parseLocalTimeInfo(byteArrayOf(-128, 0))!!.offset)
        assertNull(CurrentTimeProtocol.parseLocalTimeInfo(byteArrayOf(-24, 255.toByte()))!!.offset)
        assertNull(CurrentTimeProtocol.parseLocalTimeInfo(byteArrayOf(1)))
    }

    @Test
    fun `skew compares the iPhone instant with the watch clock`() {
        val t = CurrentTimeProtocol.parseCurrentTime(ct)!!
        val info = CurrentTimeProtocol.parseLocalTimeInfo(byteArrayOf(-24, 0))
        val iphoneMs = t.local.toInstant(ZoneOffset.ofHours(-6)).toEpochMilli()
        assertEquals(1_200L, CurrentTimeProtocol.skew(t, info, iphoneMs - 1_200)!!.toMillis())
        assertNull(CurrentTimeProtocol.skew(t, null, iphoneMs))
        val line = CurrentTimeProtocol.describe(t, info, iphoneMs - 1_200)
        assertTrue(line, line.contains("zone -06:00") && line.contains("dst +0 min") && line.contains("skew +1.2 s"))
    }
}
