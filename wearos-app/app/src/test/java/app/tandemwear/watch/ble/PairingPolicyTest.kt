package app.tandemwear.watch.ble

import app.tandemwear.watch.ble.PairingPolicy.InboundDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPolicyTest {

    private val identity = "B8:45:EB:2A:C6:84"
    private val rpa = "61:59:3B:55:9B:2F"

    @Test
    fun `newly bonded address is the single new entry`() {
        val before = setOf("AA:AA:AA:AA:AA:AA")
        val after = setOf("AA:AA:AA:AA:AA:AA", identity)
        assertEquals(identity, PairingPolicy.newlyBondedAddress(before, after))
    }

    @Test
    fun `newly bonded address ignores case differences`() {
        val before = setOf("aa:aa:aa:aa:aa:aa")
        val after = setOf("AA:AA:AA:AA:AA:AA", identity.lowercase())
        assertEquals(identity, PairingPolicy.newlyBondedAddress(before, after))
    }

    @Test
    fun `no new entry or several new entries is ambiguous`() {
        assertNull(PairingPolicy.newlyBondedAddress(setOf(identity), setOf(identity)))
        assertNull(PairingPolicy.newlyBondedAddress(emptySet(), setOf(identity, "CC:CC:CC:CC:CC:CC")))
    }

    @Test
    fun `inbound from the paired identity address attaches`() {
        assertEquals(
            InboundDecision.ATTACH,
            PairingPolicy.onInboundLink(identity.lowercase(), isBonded = true, pairedAddress = identity, pairingWindowOpen = false)
        )
    }

    @Test
    fun `unbonded rpa attaches only while pairing`() {
        assertEquals(
            InboundDecision.ATTACH,
            PairingPolicy.onInboundLink(rpa, isBonded = false, pairedAddress = null, pairingWindowOpen = true)
        )
        assertEquals(
            InboundDecision.IGNORE,
            PairingPolicy.onInboundLink(rpa, isBonded = false, pairedAddress = null, pairingWindowOpen = false)
        )
        assertEquals(
            InboundDecision.IGNORE,
            PairingPolicy.onInboundLink(rpa, isBonded = false, pairedAddress = identity, pairingWindowOpen = false)
        )
    }

    @Test
    fun `other bonded device is probed, not trusted`() {
        assertEquals(
            InboundDecision.PROBE,
            PairingPolicy.onInboundLink("CC:CC:CC:CC:CC:CC", isBonded = true, pairedAddress = identity, pairingWindowOpen = false)
        )
    }

    @Test
    fun `auth errors and key loss reasons are classified`() {
        assertTrue(PairingPolicy.isAuthError(0x05))
        assertTrue(PairingPolicy.isAuthError(0x0F))
        assertTrue(PairingPolicy.isAuthError(0x89))
        assertFalse(PairingPolicy.isAuthError(0x00))
        assertFalse(PairingPolicy.isAuthError(0x85))

        assertTrue(PairingPolicy.isKeyLossDisconnect(0x06))
        assertTrue(PairingPolicy.isKeyLossDisconnect(0x3D))
        assertFalse(PairingPolicy.isKeyLossDisconnect(0x08)) // supervision timeout = out of range
        assertFalse(PairingPolicy.isKeyLossDisconnect(0x13)) // remote user terminated
    }

    @Test
    fun `key loss needs consecutive signals and resets on a good session`() {
        val detector = KeyLossDetector(threshold = 2)
        assertFalse(detector.onSignal())
        detector.onSessionEstablished()
        assertFalse(detector.onSignal())
        assertTrue(detector.onSignal())
    }
}
