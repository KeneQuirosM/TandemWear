package app.tandemwear.watch.ui.home

import app.tandemwear.watch.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStatusTest {

    private fun map(state: ConnectionState, bt: Boolean = true, paired: Boolean = true) =
        HomeStatus.from(state, bluetoothOn = bt, hasPairedPhone = paired)

    @Test
    fun `bluetooth off wins over any connection state`() {
        assertEquals(HomeStatus.BluetoothOff, map(ConnectionState.Connected("iPhone"), bt = false))
        assertEquals(HomeStatus.BluetoothOff, map(ConnectionState.Idle, bt = false, paired = false))
        assertEquals(HomeStatus.Action.OPEN_BLUETOOTH_SETTINGS, HomeStatus.BluetoothOff.action)
    }

    @Test
    fun `connected shows the phone name and no action`() {
        val status = map(ConnectionState.Connected("Kene's iPhone", sessionId = 3))
        assertEquals(HomeStatus.Connected("Kene's iPhone"), status)
        assertEquals(HomeStatus.Action.NONE, status.action)
    }

    @Test
    fun `paired phone states`() {
        assertEquals(HomeStatus.Reconnecting("iPhone"), map(ConnectionState.Connecting("iPhone")))
        assertEquals(HomeStatus.Reconnecting(null), map(ConnectionState.Bonding(null)))
        assertEquals(HomeStatus.Disconnected(null), map(ConnectionState.Disconnected("timeout")))
        assertEquals(HomeStatus.Disconnected(null), map(ConnectionState.Idle))
        assertEquals(HomeStatus.Disconnected("GATT 133"), map(ConnectionState.Error("GATT 133")))
        assertEquals(HomeStatus.Action.RECONNECT, map(ConnectionState.Idle).action)
        assertEquals(HomeStatus.RepairNeeded, map(ConnectionState.RepairNeeded("key missing")))
        assertEquals(HomeStatus.Action.REPAIR, HomeStatus.RepairNeeded.action)
    }

    @Test
    fun `without a paired phone everything but a session asks to pair`() {
        listOf(
            ConnectionState.Idle,
            ConnectionState.WaitingForIphone("Watch"),
            ConnectionState.Connecting(null),
            ConnectionState.Bonding(null),
            ConnectionState.Disconnected(),
            ConnectionState.Error("x")
        ).forEach { assertEquals(it.toString(), HomeStatus.NotPaired, map(it, paired = false)) }
        // A session that is already up counts as connected even before the store is written
        assertEquals(HomeStatus.Connected(null), map(ConnectionState.Connected(null), paired = false))
    }
}
