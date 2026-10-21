package app.tandemwear.watch.ancs

import app.tandemwear.watch.ancs.AncsEventFilter.Decision
import app.tandemwear.watch.ble.AncsConstants
import app.tandemwear.watch.model.AncsEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AncsEventFilterTest {

    private fun event(flags: Int, category: Int = AncsConstants.CATEGORY_SOCIAL, id: Int = AncsConstants.EVENT_ID_ADDED) =
        AncsEvent(eventId = id, eventFlags = flags, categoryId = category, categoryCount = 1, notificationUid = 42)

    @Test
    fun `new notification right after connecting is fetched`() {
        // No time-based grace window: only the PreExisting flag marks backlog
        assertEquals(Decision.FETCH, AncsEventFilter.decide(event(flags = 0)))
        assertEquals(Decision.FETCH, AncsEventFilter.decide(event(flags = AncsConstants.EVENT_FLAG_IMPORTANT)))
    }

    @Test
    fun `backlog replayed at session start is skipped`() {
        assertEquals(
            Decision.SKIP_PRE_EXISTING,
            AncsEventFilter.decide(event(flags = AncsConstants.EVENT_FLAG_PRE_EXISTING))
        )
        assertEquals(
            Decision.SKIP_PRE_EXISTING,
            AncsEventFilter.decide(event(flags = AncsConstants.EVENT_FLAG_PRE_EXISTING, id = AncsConstants.EVENT_ID_MODIFIED))
        )
    }

    @Test
    fun `call still ringing at session start is shown`() {
        assertEquals(
            Decision.FETCH,
            AncsEventFilter.decide(
                event(flags = AncsConstants.EVENT_FLAG_PRE_EXISTING, category = AncsConstants.CATEGORY_INCOMING_CALL)
            )
        )
    }
}
