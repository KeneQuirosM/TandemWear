package app.tandemwear.watch.diag

import app.tandemwear.watch.diag.LinkStats.Initiator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkStatsTest {

    @Test
    fun `initiator inference`() {
        assertEquals(Initiator.IPHONE, LinkStats.inferInitiator(viaBackgroundRequest = false, msSinceRequestOpened = 5))
        // Night of 21-10: links came up 0.1 to 18 s after the request was opened
        assertEquals(Initiator.WATCH, LinkStats.inferInitiator(true, 18_150))
        assertEquals(Initiator.WATCH, LinkStats.inferInitiator(true, 60_000))
        assertEquals(Initiator.UNKNOWN, LinkStats.inferInitiator(true, 60_001))
        assertEquals(Initiator.UNKNOWN, LinkStats.inferInitiator(true, -1))
    }

    @Test
    fun `counts per day and resets on a new day`() {
        var s = LinkStats("2026-10-21")
        repeat(12) { s = s.record("2026-10-21", Initiator.WATCH, hadAncs = false) }
        s = s.record("2026-10-21", Initiator.IPHONE, hadAncs = true)
        assertEquals("watch 12 (ANCS 0), iphone 1 (ANCS 1), unknown 0 (ANCS 0)", s.summary())

        s = s.record("2026-10-22", Initiator.WATCH, hadAncs = true)
        assertEquals("2026-10-22", s.day)
        assertEquals("watch 1 (ANCS 1), iphone 0 (ANCS 0), unknown 0 (ANCS 0)", s.summary())
    }

    @Test
    fun `encode and decode round trip, bad input ignored`() {
        val s = LinkStats("2026-10-21")
            .record("2026-10-21", Initiator.WATCH, false)
            .record("2026-10-21", Initiator.UNKNOWN, true)
        assertEquals(s, LinkStats.decode(s.encode()))
        assertNull(LinkStats.decode(null))
        assertNull(LinkStats.decode(""))
        assertEquals(LinkStats("2026-10-21"), LinkStats.decode("2026-10-21|x:1:1|w:a:0"))
    }
}
