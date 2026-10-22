package app.tandemwear.watch.notify

import app.tandemwear.watch.data.settings.AppRule
import app.tandemwear.watch.data.settings.QuietHours
import app.tandemwear.watch.data.settings.TandemSettings
import app.tandemwear.watch.notify.NotificationPolicy.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ConversationPolicyTest {

    private val wa = "net.whatsapp.WhatsApp"
    private val mom = Conversation(wa, "Mamá")
    private val noon = 12 * 60
    private val night = 23 * 60
    private val now = 1_000_000L
    private val quiet = QuietHours(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)

    private fun decide(s: TandemSettings, title: String, minute: Int = noon, app: String = wa) =
        NotificationPolicy.decide(s, app, minute, title, now)

    @Test
    fun `conversation matching ignores case, accents and spaces but not the app`() {
        assertTrue(mom.matches(wa, "mama"))
        assertTrue(mom.matches(wa, "  MAMÁ "))
        assertFalse(mom.matches("com.apple.MobileSMS", "Mamá"))
        assertFalse(mom.matches(wa, "Papá"))
    }

    @Test
    fun `encoding round trip`() {
        assertEquals(mom, Conversation.decode(mom.encode()))
        assertEquals(mom to 42L, Conversation.decodeMuted(Conversation.encodeMuted(mom, 42L)))
        assertNull(Conversation.decode("no separator"))
        assertNull(Conversation.decodeMuted("garbage"))
    }

    @Test
    fun `silenced conversation is silent until it expires`() {
        val s = TandemSettings(mutedConversations = mapOf(mom to now + 60_000))
        assertEquals(Alert.SILENT, decide(s, "mama").alert)
        assertTrue(decide(s, "mama").show)
        assertEquals(Alert.ALERT, decide(s, "Papá").alert)
        val expired = TandemSettings(mutedConversations = mapOf(mom to now - 1))
        assertEquals(Alert.ALERT, decide(expired, "Mamá").alert)
    }

    @Test
    fun `VIP rings even when its app is muted`() {
        val s = TandemSettings(appRules = mapOf(wa to AppRule(muted = true)), vips = listOf(mom))
        assertEquals(Alert.ALERT, decide(s, "Mamá").alert)
        assertEquals(Alert.SILENT, decide(s, "Papá").alert)
    }

    @Test
    fun `VIP in quiet hours rings only if allowed`() {
        val off = TandemSettings(quietHours = quiet, vips = listOf(mom))
        assertEquals(Alert.SILENT, decide(off, "Mamá", night).alert)
        val on = off.copy(vipRingsInQuietHours = true)
        assertEquals(Alert.ALERT, decide(on, "Mamá", night).alert)
        assertEquals(Alert.SILENT, decide(on, "Papá", night).alert)
    }

    @Test
    fun `disabled app shows nothing, VIP included`() {
        val s = TandemSettings(appRules = mapOf(wa to AppRule(disabled = true)), vips = listOf(mom))
        assertFalse(decide(s, "Mamá").show)
    }

    @Test
    fun `silencing wins over VIP`() {
        val s = TandemSettings(vips = listOf(mom), mutedConversations = mapOf(mom to now + 1))
        assertEquals(Alert.SILENT, decide(s, "Mamá").alert)
    }

    @Test
    fun `blank title never matches a conversation`() {
        val s = TandemSettings(appRules = mapOf(wa to AppRule(muted = true)), vips = listOf(Conversation(wa, "x")))
        assertEquals(Alert.SILENT, decide(s, "").alert)
    }

    @Test
    fun `mute durations`() {
        val zone = ZoneId.of("America/Costa_Rica")
        val t = ZonedDateTime.of(2026, 10, 22, 21, 30, 0, 0, zone)
        val ms = t.toInstant().toEpochMilli()
        assertEquals(ms + 15 * 60_000, MuteDuration.MINUTES_15.untilMillis(t))
        assertEquals(ms + 3_600_000, MuteDuration.HOUR_1.untilMillis(t))
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            MuteDuration.UNTIL_TOMORROW_MORNING.untilMillis(t)
        )
        // After midnight: still the next calendar day at 07:00
        val early = ZonedDateTime.of(2026, 10, 23, 2, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            MuteDuration.UNTIL_TOMORROW_MORNING.untilMillis(early)
        )
    }

    @Test
    fun `mutedUntil returns only active silences`() {
        val s = TandemSettings(mutedConversations = mapOf(mom to now + 5))
        assertEquals(now + 5, s.mutedUntil(wa, "mama", now))
        assertNull(s.mutedUntil(wa, "mama", now + 5))
    }
}
