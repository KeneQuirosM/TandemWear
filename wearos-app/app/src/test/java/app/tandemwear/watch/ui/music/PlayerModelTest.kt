package app.tandemwear.watch.ui.music

import app.tandemwear.watch.ams.Entity
import app.tandemwear.watch.ams.MusicSession
import app.tandemwear.watch.ams.PlayerAttribute
import app.tandemwear.watch.ams.RemoteCommand
import app.tandemwear.watch.ams.TrackAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerModelTest {

    private fun eu(entity: Entity, attr: Int, value: String) =
        byteArrayOf(entity.id.toByte(), attr.toByte(), 0) + value.toByteArray(Charsets.UTF_8)

    private val subscribed = MusicSession.NONE.onStatus(available = true, subscribed = true)

    private val spotify = subscribed
        .onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.NAME, "Spotify"), 0)
        .onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.PLAYBACK_INFO, "1,1.0,30"), 0)
        .onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.VOLUME, "0.25"), 0)
        .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.TITLE, "Labios compartidos"), 0)
        .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.ARTIST, "Maná"), 0)
        .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.DURATION, "120"), 0)

    @Test
    fun `states before playing`() {
        assertEquals(PlayerModel.MusicOff, PlayerModel.from(spotify, musicEnabled = false, nowMs = 0))
        assertEquals(PlayerModel.Unavailable, PlayerModel.from(MusicSession.NONE, true, 0))
        assertEquals(PlayerModel.Unavailable, PlayerModel.from(MusicSession.NONE.onStatus(false, false), true, 0))
        assertEquals(PlayerModel.NothingPlaying, PlayerModel.from(subscribed, true, 0))
    }

    @Test
    fun `progress advances by itself between updates`() {
        val at0 = PlayerModel.from(spotify, true, 0) as PlayerModel.Playing
        val at30 = PlayerModel.from(spotify, true, 30_000) as PlayerModel.Playing
        assertEquals(0.25f, at0.progress!!, 0.001f)
        assertEquals(0.5f, at30.progress!!, 0.001f)
        assertEquals("1:00", PlayerModel.formatTime(at30.positionSeconds))
        assertEquals(0.25f, at0.volume!!, 0.001f)
        // Never past the end
        assertEquals(1f, (PlayerModel.from(spotify, true, 600_000) as PlayerModel.Playing).progress!!, 0.001f)
    }

    @Test
    fun `controls follow the supported commands`() {
        val limited = spotify.onSupportedCommands(byteArrayOf(0, 1)) // play, pause only
        val model = PlayerModel.from(limited, true, 0) as PlayerModel.Playing
        assertTrue(model.canTogglePlay)
        assertFalse(model.canNext)
        assertFalse(model.canVolume)
        assertEquals(RemoteCommand.PAUSE, PlayerModel.playPauseCommand(limited))
        assertEquals(RemoteCommand.TOGGLE_PLAY_PAUSE, PlayerModel.playPauseCommand(spotify))
    }

    @Test
    fun `time format`() {
        assertEquals("0:05", PlayerModel.formatTime(5.9f))
        assertEquals("3:07", PlayerModel.formatTime(187f))
        assertEquals("1:02:05", PlayerModel.formatTime(3725f))
        assertNull(PlayerModel.formatTime(null))
        assertNull(PlayerModel.formatTime(-1f))
        assertNull((PlayerModel.from(subscribed.onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.NAME, "Podcasts"), 0), true, 0) as PlayerModel.Playing).progress)
    }
}
