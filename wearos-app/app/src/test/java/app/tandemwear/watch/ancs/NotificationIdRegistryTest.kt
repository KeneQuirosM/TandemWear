package app.tandemwear.watch.ancs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdRegistryTest {

    @Test
    fun `same uid in the same session keeps its id (MODIFIED updates in place)`() {
        val registry = NotificationIdRegistry()
        registry.startSession(1)
        assertEquals(registry.idFor(7), registry.idFor(7))
    }

    @Test
    fun `renumbered uid after reconnect gets a new id, so it alerts instead of replacing`() {
        val registry = NotificationIdRegistry()
        registry.startSession(1)
        val oldId = registry.idFor(3) // old notification still on the watch
        registry.endSession()

        registry.startSession(2) // iPhone renumbers: uid 3 is now a different notification
        val newId = registry.idFor(3)
        assertNotEquals(oldId, newId)
    }

    @Test
    fun `removed event only cancels notifications of the current session`() {
        val registry = NotificationIdRegistry()
        registry.startSession(1)
        registry.idFor(5)
        registry.endSession()

        registry.startSession(2)
        assertNull("uid 5 of the old session must not be cancelled", registry.remove(5))

        val id = registry.idFor(5)
        assertEquals(id, registry.remove(5))
        assertNull(registry.remove(5))
    }

    @Test
    fun `session tokens identify stale actions`() {
        val registry = NotificationIdRegistry()
        assertFalse(registry.isSessionActive)
        registry.startSession(100)
        assertTrue(registry.isCurrent(100))
        assertFalse(registry.isCurrent(99))
        registry.endSession()
        assertFalse(registry.isCurrent(100))
    }

    @Test
    fun `next id is persisted and survives a restart`() {
        var persisted = 0
        val first = NotificationIdRegistry(onNextIdChanged = { persisted = it })
        first.startSession(1)
        val a = first.idFor(1)
        val b = first.idFor(2)

        val restarted = NotificationIdRegistry(initialNextId = persisted)
        restarted.startSession(2)
        val c = restarted.idFor(1)
        assertTrue(c != a && c != b)
    }

    @Test
    fun `ids stay above reserved range and wrap around`() {
        val registry = NotificationIdRegistry(initialNextId = NotificationIdRegistry.LAST_ID)
        registry.startSession(1)
        assertEquals(NotificationIdRegistry.LAST_ID, registry.idFor(1))
        assertEquals(NotificationIdRegistry.FIRST_ID, registry.idFor(2))

        val low = NotificationIdRegistry(initialNextId = 5)
        low.startSession(1)
        assertEquals(NotificationIdRegistry.FIRST_ID, low.idFor(1))
    }
}
