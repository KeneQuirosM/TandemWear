package app.tandemwear.watch.ancs

import app.tandemwear.watch.ancs.CallLaunchPolicy.Start
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLaunchPolicyTest {

    @Test
    fun `full-screen intent is the primary path when allowed`() {
        assertEquals(Start.FULL_SCREEN_THEN_CHECK, CallLaunchPolicy.initial(fullScreenAllowed = true))
    }

    @Test
    fun `direct start at once when full-screen is refused`() {
        assertEquals(Start.DIRECT_NOW, CallLaunchPolicy.initial(fullScreenAllowed = false))
    }

    @Test
    fun `fallback only for the same ringing call with no screen`() {
        assertTrue(CallLaunchPolicy.needsFallback(sameCallStillRinging = true, screenVisible = false))
        assertFalse(CallLaunchPolicy.needsFallback(sameCallStillRinging = true, screenVisible = true))
        // Answered, declined or ended meanwhile: never pop a screen
        assertFalse(CallLaunchPolicy.needsFallback(sameCallStillRinging = false, screenVisible = false))
    }
}
