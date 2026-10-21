package app.tandemwear.watch.notify

import app.tandemwear.watch.data.settings.AppRule
import app.tandemwear.watch.data.settings.QuietHours
import app.tandemwear.watch.data.settings.TandemSettings
import app.tandemwear.watch.notify.NotificationPolicy.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {

    private val whatsapp = "net.whatsapp.WhatsApp"
    private val noon = 12 * 60
    private val night = 23 * 60

    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun `defaults show and alert`() {
        val d = NotificationPolicy.decide(TandemSettings(), whatsapp, noon)
        assertTrue(d.show)
        assertEquals(Alert.ALERT, d.alert)
        assertFalse(d.hideContent)
        assertFalse(d.privateOnLockScreen)
    }

    @Test
    fun `per app rules`() {
        val settings = TandemSettings(
            appRules = mapOf(
                "a.disabled" to AppRule(disabled = true, muted = true),
                "a.muted" to AppRule(muted = true),
                "a.hidden" to AppRule(hideContent = true)
            )
        )
        assertFalse(NotificationPolicy.decide(settings, "a.disabled", noon).show)
        NotificationPolicy.decide(settings, "a.muted", noon).let {
            assertTrue(it.show); assertEquals(Alert.SILENT, it.alert); assertFalse(it.hideContent)
        }
        NotificationPolicy.decide(settings, "a.hidden", noon).let {
            assertEquals(Alert.ALERT, it.alert); assertTrue(it.hideContent); assertTrue(it.privateOnLockScreen)
        }
        // Rules are per app: others unaffected
        assertEquals(Alert.ALERT, NotificationPolicy.decide(settings, whatsapp, noon).alert)
    }

    @Test
    fun `lock screen privacy is global`() {
        val d = NotificationPolicy.decide(TandemSettings(hideContentOnLockScreen = true), whatsapp, noon)
        assertTrue(d.privateOnLockScreen)
        assertFalse(d.hideContent)
    }

    @Test
    fun `quiet hours silence but still show`() {
        val settings = TandemSettings(quietHours = QuietHours(enabled = true, startMinute = hm(22), endMinute = hm(7)))
        NotificationPolicy.decide(settings, whatsapp, night).let {
            assertTrue(it.show); assertEquals(Alert.SILENT, it.alert)
        }
        assertEquals(Alert.ALERT, NotificationPolicy.decide(settings, whatsapp, noon).alert)
    }

    @Test
    fun `quiet window crossing midnight`() {
        val q = QuietHours(enabled = true, startMinute = hm(22), endMinute = hm(7))
        assertFalse(q.isActiveAt(hm(21, 59)))
        assertTrue(q.isActiveAt(hm(22)))
        assertTrue(q.isActiveAt(hm(23, 59)))
        assertTrue(q.isActiveAt(0))
        assertTrue(q.isActiveAt(hm(6, 59)))
        assertFalse(q.isActiveAt(hm(7)))
        assertFalse(q.isActiveAt(noon))
    }

    @Test
    fun `quiet window within one day, empty and disabled`() {
        val day = QuietHours(enabled = true, startMinute = hm(9), endMinute = hm(17))
        assertFalse(day.isActiveAt(hm(8, 59)))
        assertTrue(day.isActiveAt(hm(9)))
        assertFalse(day.isActiveAt(hm(17)))
        assertFalse(QuietHours(enabled = true, startMinute = hm(8), endMinute = hm(8)).isActiveAt(hm(8)))
        assertFalse(day.copy(enabled = false).isActiveAt(hm(10)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range minutes are rejected`() {
        QuietHours(startMinute = 24 * 60)
    }

    @Test
    fun `calls ring during quiet hours only with the exception`() {
        val withCalls = TandemSettings(quietHours = QuietHours(enabled = true, startMinute = hm(22), endMinute = hm(7), allowCalls = true))
        val noCalls = withCalls.copy(quietHours = withCalls.quietHours.copy(allowCalls = false))
        assertTrue(NotificationPolicy.shouldRingForCall(withCalls, night))
        assertFalse(NotificationPolicy.shouldRingForCall(noCalls, night))
        assertTrue(NotificationPolicy.shouldRingForCall(noCalls, noon))
        // Disabling the Phone app never silences calls
        val phoneDisabled = TandemSettings(appRules = mapOf("com.apple.mobilephone" to AppRule(disabled = true)))
        assertTrue(NotificationPolicy.shouldRingForCall(phoneDisabled, night))
    }
}
