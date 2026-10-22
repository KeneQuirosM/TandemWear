package app.tandemwear.watch.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogPolicyTest {

    @Test
    fun `a call that ends without an outcome was missed`() {
        assertEquals(CallOutcome.MISSED, CallLogPolicy.outcomeOnEnd(null))
        assertEquals(CallOutcome.ANSWERED, CallLogPolicy.outcomeOnEnd(CallOutcome.ANSWERED))
        assertEquals(CallOutcome.DECLINED_ON_WATCH, CallLogPolicy.outcomeOnEnd(CallOutcome.DECLINED_ON_WATCH))
    }

    @Test
    fun `missed call notification is merged with the call that rang`() {
        val recent = listOf("Mamá" to 100_000L)
        assertTrue(CallLogPolicy.isAlreadyLogged("mama", 160_000, recent))
        assertFalse(CallLogPolicy.isAlreadyLogged("Papá", 160_000, recent))
        assertFalse(CallLogPolicy.isAlreadyLogged("Mamá", 100_000 + CallLogPolicy.SAME_CALL_WINDOW_MS + 1, recent))
    }

    @Test
    fun `unknown stored outcome reads as missed`() {
        assertEquals(CallOutcome.MISSED, CallOutcome.fromName("SOMETHING_NEW"))
        assertEquals(CallOutcome.ANSWERED_ON_WATCH, CallOutcome.fromName("ANSWERED_ON_WATCH"))
    }
}
