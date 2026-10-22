package app.tandemwear.watch.battery

import app.tandemwear.watch.ble.AncsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class BatteryProtocolTest {

    @Test
    fun `level is one byte between 0 and 100`() {
        assertEquals(62, BatteryProtocol.parseLevel(byteArrayOf(62)))
        assertEquals(0, BatteryProtocol.parseLevel(byteArrayOf(0)))
        assertEquals(100, BatteryProtocol.parseLevel(byteArrayOf(100)))
        assertNull(BatteryProtocol.parseLevel(byteArrayOf(101)))
        assertNull(BatteryProtocol.parseLevel(byteArrayOf(0xFF.toByte())))
        assertNull(BatteryProtocol.parseLevel(byteArrayOf()))
        assertNull(BatteryProtocol.parseLevel(byteArrayOf(50, 1)))
    }

    @Test
    fun `16-bit SIG UUIDs are shortened`() {
        assertEquals("180f", BatteryProtocol.shortName(BatteryProtocol.SERVICE_UUID))
        assertEquals("2a19", BatteryProtocol.shortName(BatteryProtocol.LEVEL_UUID))
        assertEquals("7905f431-b5ce-4e99-a40f-4b1e122d00d0", BatteryProtocol.shortName(AncsConstants.ANCS_SERVICE_UUID))
    }

    @Test
    fun `services line is sorted and names known services`() {
        val gap = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        val line = BatteryProtocol.describeServices(
            listOf(BatteryProtocol.SERVICE_UUID, gap, AncsConstants.ANCS_SERVICE_UUID),
            mapOf(BatteryProtocol.SERVICE_UUID to "Battery", AncsConstants.ANCS_SERVICE_UUID to "ANCS")
        )
        assertEquals("1800, 180f(Battery), 7905f431-b5ce-4e99-a40f-4b1e122d00d0(ANCS)", line)
    }
}
