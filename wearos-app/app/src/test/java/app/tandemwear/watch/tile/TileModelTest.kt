package app.tandemwear.watch.tile

import app.tandemwear.watch.tile.TileModel.LastNotification
import app.tandemwear.watch.tile.TileModel.Tone
import app.tandemwear.watch.ui.home.HomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileModelTest {

    private val msg = LastNotification("WhatsApp", "Ana", "¿Vamos hoy?", hidden = false)

    @Test
    fun `tone follows the connection state`() {
        assertEquals(Tone.CONNECTED, TileModel(HomeStatus.Connected("iPhone"), 0, null).tone)
        assertEquals(Tone.RECONNECTING, TileModel(HomeStatus.Reconnecting(null), 0, null).tone)
        assertEquals(Tone.IDLE, TileModel(HomeStatus.Disconnected(null), 0, null).tone)
        assertEquals(Tone.IDLE, TileModel(HomeStatus.NotPaired, 0, null).tone)
        assertEquals(Tone.ATTENTION, TileModel(HomeStatus.RepairNeeded, 0, null).tone)
        assertEquals(Tone.ATTENTION, TileModel(HomeStatus.BluetoothOff, 0, null).tone)
    }

    @Test
    fun `last notification respects hidden content`() {
        val shown = TileModel(HomeStatus.Connected(null), 1, msg)
        assertEquals("Ana", shown.lastTitle)
        assertEquals("¿Vamos hoy?", shown.lastMessage(hideOnTile = false))
        assertNull(shown.lastMessage(hideOnTile = true))

        val hidden = shown.copy(last = msg.copy(title = "", message = "", hidden = true))
        assertEquals("WhatsApp", hidden.lastTitle)
        assertNull(hidden.lastMessage(hideOnTile = false))

        assertNull(TileModel(HomeStatus.Connected(null), 0, null).lastTitle)
    }

    @Test
    fun `complication short text is the pending count`() {
        assertEquals("0", TileModel(HomeStatus.Connected(null), 0, null).shortText)
        assertEquals("7", TileModel(HomeStatus.Connected(null), 7, null).shortText)
        assertEquals("99+", TileModel(HomeStatus.Connected(null), 120, null).shortText)
    }
}
