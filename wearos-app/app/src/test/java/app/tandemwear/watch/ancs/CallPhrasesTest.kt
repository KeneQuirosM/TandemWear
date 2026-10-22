package app.tandemwear.watch.ancs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPhrasesTest {

    private val whatsApp = "net.whatsapp.WhatsApp"

    private fun ringing(title: String, message: String, app: String = whatsApp, positive: Boolean = false) =
        CallPhrases.isIncomingCall(isCallCategory = false, appIdentifier = app, hasPositiveAction = positive, title = title, message = message)

    @Test
    fun `normalize drops accents, case and extra spaces`() {
        assertEquals("te esta llamando", CallPhrases.normalize("  Te  ESTÁ llamando "))
        assertEquals("videollamada", CallPhrases.normalize("Vídeollamada"))
    }

    @Test
    fun `spanish whatsapp calls ring`() {
        assertTrue(ringing("Mamá", "Llamada de voz entrante"))
        assertTrue(ringing("Mamá", "Videollamada entrante"))
        assertTrue(ringing("Trabajo", "Llamada de audio entrante"))
        assertTrue(ringing("Ana", "Te está llamando"))
        assertTrue(ringing("Ana", "LLAMADA ENTRANTE"))
    }

    @Test
    fun `english incoming and calling phrases ring`() {
        assertTrue(ringing("Mom", "Incoming voice call"))
        assertTrue(ringing("Mom", "Incoming Video Call"))
        assertTrue(ringing("Mom", "☎ Incoming audio call"))
        assertTrue(ringing("Bob is calling", ""))
        assertTrue(ringing("Bob", "Calling you…"))
    }

    @Test
    fun `bare english call words do not ring`() {
        assertFalse(ringing("Mom", "Video call"))
        assertFalse(ringing("Mom", "Voice call"))
        assertFalse(ringing("Mom", "Audio call"))
        assertFalse(ringing("Ana", "Video call later?"))
        assertFalse(ringing("Mom", "Ringing…"))
        // The phone emoji alone is chat text too
        assertFalse(ringing("Mom", "☎ Voice Call"))
    }

    @Test
    fun `unrecognised english call texts are logged for the table`() {
        assertEquals("mom | video call", CallPhrases.unrecognisedCallApp(whatsApp, false, "Mom", "Video call"))
    }

    @Test
    fun `missed or ended calls never ring`() {
        assertFalse(ringing("Mamá", "Llamada de voz perdida"))
        assertFalse(ringing("Mamá", "Videollamada perdida"))
        assertFalse(ringing("Mom", "Missed voice call"))
        assertFalse(ringing("Mom", "Video call ended"))
    }

    @Test
    fun `ordinary messages do not ring`() {
        assertFalse(ringing("Ana", "¿Hacemos una videollamada mañana?"))
        assertFalse(ringing("Ana", "Te mando la llamada de voz luego"))
        assertFalse(ringing("Ana", "Recommended for you"))
        assertFalse(ringing("Ana", "Hola, ¿cómo estás?"))
    }

    @Test
    fun `text alone is not enough outside call apps without an answer action`() {
        assertFalse(ringing("Ana", "Llamada entrante", app = "com.apple.MobileSMS"))
        assertTrue(ringing("Ana", "Llamada entrante", app = "com.example.voip", positive = true))
    }

    @Test
    fun `category IncomingCall always rings`() {
        assertTrue(CallPhrases.isIncomingCall(true, "com.apple.mobilephone", false, "", ""))
    }

    @Test
    fun `only call-like unrecognised texts of call apps are logged`() {
        assertNotNull(CallPhrases.unrecognisedCallApp(whatsApp, false, "Ana", "Llamada grupal de WhatsApp"))
        // Ordinary chat text is never logged (privacy)
        assertNull(CallPhrases.unrecognisedCallApp(whatsApp, false, "Ana", "Hola, ¿cómo estás?"))
        assertNull(CallPhrases.unrecognisedCallApp(whatsApp, true, "Ana", "Llamada entrante"))
        assertNull(CallPhrases.unrecognisedCallApp("com.apple.MobileSMS", false, "Ana", "llamame"))
    }
}
