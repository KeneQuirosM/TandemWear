package app.tandemwear.watch.ble

import app.tandemwear.watch.ble.GattOperationQueue.OpKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GattOperationQueueTest {

    private val cp = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    private val ds = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")

    @Test
    fun `only one operation is in flight at a time`() = runTest {
        val q = GattOperationQueue(backgroundScope)
        val started = mutableListOf<String>()

        val a = q.enqueue("a", OpKey.WriteDescriptor(ds)) { started += "a"; true }
        val b = q.enqueue("b", OpKey.WriteCharacteristic(cp)) { started += "b"; true }

        assertEquals(listOf("a"), started)
        assertEquals(1, q.pendingCount)

        q.onComplete(OpKey.WriteDescriptor(ds), true)
        assertTrue(a.await())
        assertEquals(listOf("a", "b"), started)

        q.onComplete(OpKey.WriteCharacteristic(cp), true)
        assertTrue(b.await())
        assertFalse(q.isBusy)
    }

    @Test
    fun `rejected start fails that op and moves on`() = runTest {
        val q = GattOperationQueue(backgroundScope)
        val a = q.enqueue("a", OpKey.Mtu) { false }
        var bStarted = false
        q.enqueue("b", OpKey.DiscoverServices) { bStarted = true; true }

        assertFalse(a.await())
        assertTrue(bStarted)
        assertTrue(q.isBusy)
    }

    @Test
    fun `security exception counts as rejection`() = runTest {
        val q = GattOperationQueue(backgroundScope)
        val a = q.enqueue("a", OpKey.Mtu) { throw SecurityException("no BLUETOOTH_CONNECT") }
        assertFalse(a.await())
        assertFalse(q.isBusy)
    }

    @Test
    fun `mismatched completion is ignored`() = runTest {
        val q = GattOperationQueue(backgroundScope)
        val a = q.enqueue("a", OpKey.WriteCharacteristic(cp)) { true }

        q.onComplete(OpKey.WriteDescriptor(ds), true)
        assertFalse(a.isCompleted)

        q.onComplete(OpKey.WriteCharacteristic(cp), false)
        assertFalse(a.await())
    }

    @Test
    fun `timeout fails the op and starts the next one`() = runTest {
        val q = GattOperationQueue(backgroundScope, timeoutMs = 1_000)
        val a = q.enqueue("a", OpKey.ReadCharacteristic(cp)) { true }
        var bStarted = false
        q.enqueue("b", OpKey.Mtu) { bStarted = true; true }

        advanceTimeBy(999)
        runCurrent()
        assertFalse(a.isCompleted)

        advanceTimeBy(2)
        runCurrent()
        assertFalse(a.await())
        assertTrue(bStarted)

        // A late callback for the timed-out op must not complete the next one
        q.onComplete(OpKey.ReadCharacteristic(cp), true)
        assertTrue(q.isBusy)
    }

    @Test
    fun `completion before timeout cancels the timer`() = runTest {
        val q = GattOperationQueue(backgroundScope, timeoutMs = 1_000)
        val a = q.enqueue("a", OpKey.Mtu) { true }
        q.onComplete(OpKey.Mtu, true)
        val b = q.enqueue("b", OpKey.DiscoverServices) { true }

        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(a.await())
        // b started at t=0 too, so it times out on its own clock, not a's
        assertFalse(b.await())
    }

    @Test
    fun `clear fails in-flight and queued ops`() = runTest {
        val q = GattOperationQueue(backgroundScope)
        val a = q.enqueue("a", OpKey.Mtu) { true }
        val b = q.enqueue("b", OpKey.DiscoverServices) { true }

        q.clear()

        assertFalse(a.await())
        assertFalse(b.await())
        assertFalse(q.isBusy)
        assertEquals(0, q.pendingCount)
    }

    @Test
    fun `per-operation timeout overrides the default`() = runTest {
        val q = GattOperationQueue(backgroundScope, timeoutMs = 5_000)
        val ams = q.enqueue("ams write", OpKey.WriteCharacteristic(cp), timeoutMs = 1_500) { true }
        var ancsStarted = false
        q.enqueue("ancs", OpKey.WriteCharacteristic(ds)) { ancsStarted = true; true }

        advanceTimeBy(1_501)
        runCurrent()
        assertFalse(ams.await())
        assertTrue("ANCS waited at most the short AMS timeout", ancsStarted)
    }
}
