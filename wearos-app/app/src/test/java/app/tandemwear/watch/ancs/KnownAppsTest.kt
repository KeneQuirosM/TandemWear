package app.tandemwear.watch.ancs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownAppsTest {

    @Test
    fun `latin american essentials are named`() {
        mapOf(
            "net.whatsapp.WhatsApp" to "WhatsApp",
            "com.burbn.instagram" to "Instagram",
            "ph.telegra.Telegraph" to "Telegram",
            "com.facebook.Messenger" to "Messenger",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.google.Gmail" to "Gmail",
            "com.microsoft.Office.Outlook" to "Outlook",
            "com.waze.iphone" to "Waze",
            "com.ubercab.UberClient" to "Uber",
            "com.netflix.Netflix" to "Netflix",
            "com.spotify.client" to "Spotify"
        ).forEach { (id, name) -> assertEquals(id, name, KnownApps.displayName(id)) }
    }

    @Test
    fun `australian and indian apps were removed`() {
        listOf("au.com.westpac.banking", "com.jio.myjio", "au.com.auspost", "au.com.hotdoc.app")
            .forEach { assertFalse(it, KnownApps.isKnown(it)) }
    }

    @Test
    fun `unknown apps fall back to the last bundle component`() {
        assertEquals("BancaMovil", KnownApps.displayName("cr.fi.bank.BancaMovil"))
        assertEquals("", KnownApps.displayName(""))
        assertTrue(KnownApps.isKnown("com.apple.MobileSMS"))
    }
}
