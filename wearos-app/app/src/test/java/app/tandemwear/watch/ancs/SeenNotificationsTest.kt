package app.tandemwear.watch.ancs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SeenNotificationsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 24 * 60 * 60_000L

    @Test
    fun `fingerprint needs a date and covers app, title, text and date`() {
        assertNull(SeenNotifications.fingerprint("a", "t", "m", null))
        assertNull(SeenNotifications.fingerprint("a", "t", "m", " "))
        val base = SeenNotifications.fingerprint("com.apple.reminders", "Pastilla dos", "", "20261020T212400")!!
        assertEquals(64, base.length)
        assertFalse(base.contains("Pastilla"))
        assertNotEquals(base, SeenNotifications.fingerprint("com.apple.reminders", "Pastilla dos", "", "20261021T212400"))
        assertNotEquals(base, SeenNotifications.fingerprint("com.apple.reminders", "Pastillas", "", "20261020T212400"))
        assertNotEquals(
            SeenNotifications.fingerprint("a", "b\u0000c", "", "d"),
            SeenNotifications.fingerprint("a", "b", "c", "d")
        )
    }

    @Test
    fun `remembers across instances and forgets after a week`() {
        val file = tmp.root.resolve("seen.txt")
        val fp = SeenNotifications.fingerprint("a", "t", "m", "20261020T212400")!!
        SeenNotifications(file).add(fp, nowMs = 1_000)
        assertTrue(SeenNotifications(file).contains(fp, nowMs = 1_000 + 6 * day))
        assertFalse(SeenNotifications(file).contains(fp, nowMs = 1_000 + 8 * day))
        assertFalse(file.readText().contains(fp)) // pruned on disk too
    }

    @Test
    fun `corrupt file is ignored and size is bounded`() {
        val file = tmp.root.resolve("seen.txt")
        file.writeText("garbage\n123 abc\n")
        val seen = SeenNotifications(file)
        assertFalse(seen.contains("x".repeat(64), 0))
        repeat(SeenNotifications.MAX_ENTRIES + 10) { seen.add("%064x".format(it), nowMs = 10) }
        assertEquals(SeenNotifications.MAX_ENTRIES, file.readLines().size)
        assertFalse(seen.contains("%064x".format(0), 10)) // oldest dropped first
        seen.clear()
        assertFalse(file.exists())
    }
}
