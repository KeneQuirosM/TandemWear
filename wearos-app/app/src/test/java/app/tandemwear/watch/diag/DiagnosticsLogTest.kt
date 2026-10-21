package app.tandemwear.watch.diag

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsLogTest {

    @After
    fun tearDown() {
        DiagnosticsLog.clear()
        DiagnosticsLog.setCapacityForTest(200)
        DiagnosticsLog.allowContent = false
    }

    @Test
    fun `keeps only the newest lines`() {
        DiagnosticsLog.setCapacityForTest(3)
        (1..5).forEach { DiagnosticsLog.log("line $it") }
        val lines = DiagnosticsLog.lines.value
        assertEquals(3, lines.size)
        assertTrue(lines.first().endsWith("line 3"))
        assertTrue(lines.last().endsWith("line 5"))
    }

    @Test
    fun `notification preview hides content unless allowed`() {
        DiagnosticsLog.allowContent = false
        val hidden = DiagnosticsLog.notificationPreview("net.whatsapp.WhatsApp", "Mamá", "Hola")
        assertFalse(hidden.contains("Mamá"))
        assertTrue(hidden.contains("title(len)=4"))

        DiagnosticsLog.allowContent = true
        val shown = DiagnosticsLog.notificationPreview("net.whatsapp.WhatsApp", "Mamá", "Hola")
        assertTrue(shown.contains("\"Mamá\""))
    }
}
