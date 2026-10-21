package app.tandemwear.watch.ancs

import app.tandemwear.watch.ancs.RestoredNotificationRule.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RestoredNotificationRuleTest {

    private val costaRica = ZoneId.of("America/Costa_Rica") // UTC-6, no DST
    private val newYork = ZoneId.of("America/New_York")     // DST: 8 Mar and 1 Nov 2026
    private val minute = 60_000L

    private fun ms(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, zone).toInstant().toEpochMilli()

    private fun classify(
        date: String?,
        zone: ZoneId = costaRica,
        sessionStart: Long,
        now: Long,
        seen: Boolean = false,
        doubt: Boolean = false
    ) = RestoredNotificationRule.classify(date, zone, sessionStart, now, seen, doubt)

    // Night of 21-10-2026 (Costa Rica): session at 05:10:01, "Pastilla dos" from 21:24 the day before
    private val session = ms(costaRica, 2026, 10, 21, 5, 10, 1)

    @Test
    fun `reminder from last night restored after the iPhone restart`() {
        val r = classify("20261020T212400", sessionStart = session, now = session + 7 * minute)
        assertEquals(Verdict.RESTORED, r.verdict)
        assertFalse(r.clockDoubt)
    }

    @Test
    fun `fresh notification during the restore window is new`() {
        assertEquals(Verdict.NEW, classify("20261021T051500", sessionStart = session, now = session + 5 * minute).verdict)
        // Dated a few minutes before the session (clock skew, sent while reconnecting): still new
        assertEquals(Verdict.NEW, classify("20261021T050000", sessionStart = session, now = session + minute).verdict)
    }

    @Test
    fun `old date after the session window is new`() {
        // Only the first 15 minutes of a session are a restore window
        assertEquals(Verdict.NEW, classify("20261020T212400", sessionStart = session, now = session + 16 * minute).verdict)
    }

    @Test
    fun `missing or malformed dates are new`() {
        listOf(null, "", "   ", "garbage", "20261321T101010", "20261021T256000", "20261021 051500", "20261021T0515")
            .forEach { assertEquals(it.toString(), Verdict.NEW, classify(it, sessionStart = session, now = session + minute).verdict) }
    }

    @Test
    fun `date in the future raises clock doubt and stays new`() {
        val r = classify("20261021T060000", sessionStart = session, now = session + minute)
        assertEquals(Verdict.NEW, r.verdict)
        assertTrue(r.clockDoubt)
        // With doubt in the session, even a clearly old one is new
        assertEquals(Verdict.NEW, classify("20261020T212400", sessionStart = session, now = session + 2 * minute, doubt = true).verdict)
    }

    @Test
    fun `zone mismatch with the watch behind the iPhone is caught as doubt`() {
        // iPhone in New York (UTC-4 in Oct) sends 07:12 local for a brand-new notification;
        // the watch still on Costa Rica time reads it as 07:12 CR = 2 h in the future
        val now = session + 2 * minute
        val r = classify("20261021T071200", zone = costaRica, sessionStart = session, now = now)
        assertEquals(Verdict.NEW, r.verdict)
        assertTrue(r.clockDoubt)
    }

    @Test
    fun `manual watch time zone disables the rule`() {
        // Watch zone set by hand ahead of the iPhone's: a fresh notification would look old
        val r = RestoredNotificationRule.classify(
            "20261020T212400", costaRica, session, session + minute,
            seenBefore = true, sessionClockDoubt = false, watchZoneAutomatic = false
        )
        assertEquals(Verdict.NEW, r.verdict)
    }

    @Test
    fun `seen before is restored, unless the clocks are in doubt`() {
        assertEquals(Verdict.RESTORED, classify("20261021T050500", sessionStart = session, now = session + 30 * minute, seen = true).verdict)
        assertEquals(Verdict.NEW, classify("20261021T050500", sessionStart = session, now = session + 30 * minute, seen = true, doubt = true).verdict)
        assertEquals(Verdict.NEW, classify(null, sessionStart = session, now = session + minute, seen = true).verdict)
    }

    @Test
    fun `dst gap local time is not usable`() {
        // 2026-03-08 02:30 does not exist in New York (clocks jump 02:00 → 03:00)
        assertNull(RestoredNotificationRule.parseAncsDate("20260308T023000", newYork))
        val start = ms(newYork, 2026, 3, 8, 3, 5)
        assertEquals(Verdict.NEW, classify("20260308T023000", zone = newYork, sessionStart = start, now = start + minute).verdict)
    }

    @Test
    fun `dst overlap uses the later reading`() {
        // 2026-11-01 01:30 happens twice in New York (EDT then EST): take the later (EST, UTC-5)
        val instant = RestoredNotificationRule.parseAncsDate("20261101T013000", newYork)
        assertNotNull(instant)
        assertEquals(LocalDateTime.of(2026, 11, 1, 6, 30).toInstant(java.time.ZoneOffset.UTC), instant)
    }

    @Test
    fun `around a dst change everything is new`() {
        // Session right after the fall-back change: old-looking dates are not trusted
        val start = ms(newYork, 2026, 11, 1, 1, 50)
        assertEquals(Verdict.NEW, classify("20261031T200000", zone = newYork, sessionStart = start, now = start + 5 * minute).verdict)
        // A day later the same situation is judged normally
        val later = ms(newYork, 2026, 11, 2, 6, 0)
        assertEquals(Verdict.RESTORED, classify("20261101T200000", zone = newYork, sessionStart = later, now = later + 5 * minute).verdict)
    }

    @Test
    fun `near transition helper`() {
        val change = ms(newYork, 2026, 3, 8, 3, 0) // just after the jump (07:00Z)
        assertTrue(RestoredNotificationRule.nearTransition(java.time.Instant.ofEpochMilli(change + 30 * minute), newYork))
        assertFalse(RestoredNotificationRule.nearTransition(java.time.Instant.ofEpochMilli(change + 5 * 60 * minute), newYork))
        assertFalse(RestoredNotificationRule.nearTransition(java.time.Instant.ofEpochMilli(session), costaRica))
    }

    @Test
    fun `same instant read in different zones`() {
        // The rule reads the iPhone's local time in the watch's zone: equal zones = exact instant
        val cr = RestoredNotificationRule.parseAncsDate("20261021T051000", costaRica)!!
        assertEquals(ms(costaRica, 2026, 10, 21, 5, 10), cr.toEpochMilli())
        val madrid = RestoredNotificationRule.parseAncsDate("20261021T131000", ZoneId.of("Europe/Madrid"))!!
        assertEquals(cr, madrid) // 13:10 CEST == 05:10 CST
    }
}
