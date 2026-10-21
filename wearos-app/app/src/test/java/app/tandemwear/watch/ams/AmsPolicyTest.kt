package app.tandemwear.watch.ams

import app.tandemwear.watch.ams.AmsPolicy.StartMode
import app.tandemwear.watch.ams.AmsPolicy.Step
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmsPolicyTest {

    @Test
    fun `plan subscribes to entity update before writing the entities`() {
        val plan = AmsPolicy.subscriptionPlan()
        assertEquals(5, plan.size)
        assertEquals(Step.EnableNotifications(AmsProtocol.ENTITY_UPDATE_UUID, "notify Entity Update"), plan[0])
        assertEquals(AmsProtocol.REMOTE_COMMAND_UUID, (plan[1] as Step.EnableNotifications).characteristic)
        val writes = plan.drop(2).map { it as Step.Write }
        writes.forEach { assertEquals(AmsProtocol.ENTITY_UPDATE_UUID, it.characteristic) }
        assertArrayEquals(byteArrayOf(0, 0, 1, 2), writes[0].value)    // Player: name, playback info, volume
        assertArrayEquals(byteArrayOf(1, 0, 1, 2, 3), writes[1].value) // Queue: index, count, shuffle, repeat
        assertArrayEquals(byteArrayOf(2, 0, 1, 2, 3), writes[2].value) // Track: artist, album, title, duration
    }

    @Test
    fun `subscribe only with music on, ANCS active and AMS present`() {
        assertTrue(AmsPolicy.shouldSubscribe(StartMode.ON_CONNECT, true, true, true, demanded = false))
        assertFalse(AmsPolicy.shouldSubscribe(StartMode.ON_CONNECT, false, true, true, false))
        assertFalse(AmsPolicy.shouldSubscribe(StartMode.ON_CONNECT, true, false, true, false))
        assertFalse(AmsPolicy.shouldSubscribe(StartMode.ON_CONNECT, true, true, false, false))
        // On demand: only when the player or the Tile asks
        assertFalse(AmsPolicy.shouldSubscribe(StartMode.ON_DEMAND, true, true, true, demanded = false))
        assertTrue(AmsPolicy.shouldSubscribe(StartMode.ON_DEMAND, true, true, true, demanded = true))
    }

    @Test
    fun `AMS waits for an idle queue and a quiet ANCS`() {
        assertTrue(AmsPolicy.canProceed(queueBusy = false, queuePending = 0, ancsRequestsPending = 0, msSinceAncsActivity = 600))
        assertFalse(AmsPolicy.canProceed(true, 0, 0, 10_000))
        assertFalse(AmsPolicy.canProceed(false, 1, 0, 10_000))
        assertFalse(AmsPolicy.canProceed(false, 0, 1, 10_000))
        assertFalse(AmsPolicy.canProceed(false, 0, 0, 100))
    }

    @Test
    fun `only displayed text is fetched in full`() {
        assertTrue((Entity.TRACK to TrackAttribute.TITLE) in AmsPolicy.FETCH_FULL)
        assertFalse((Entity.PLAYER to PlayerAttribute.NAME) in AmsPolicy.FETCH_FULL)
    }
}
