package app.tandemwear.watch.ams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSessionTest {

    private fun eu(entity: Entity, attr: Int, value: String, flags: Int = 0) =
        byteArrayOf(entity.id.toByte(), attr.toByte(), flags.toByte()) + value.toByteArray(Charsets.UTF_8)

    private fun spotifyPlaying(now: Long = 1_000): MusicSession = MusicSession.NONE
        .onStatus(available = true, subscribed = true)
        .onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.NAME, "Spotify"), now)
        .onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.PLAYBACK_INFO, "1,1.0,42.5"), now)
        .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.TITLE, "Rayando el sol"), now)
        .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.ARTIST, "Maná"), now)
        .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.DURATION, "250.0"), now)
        .onSupportedCommands(byteArrayOf(0, 1, 2, 3, 4, 5, 6))

    @Test
    fun `spotify playing then paused`() {
        var s = spotifyPlaying()
        assertTrue(s.hasPlayer)
        assertTrue(s.media.isPlaying)
        assertEquals("Maná", s.media.artist)
        assertEquals(52.5f, s.media.positionSeconds(11_000)!!, 0.01f)

        s = s.onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.PLAYBACK_INFO, "0,0.0,60"), 20_000)
        assertTrue(s.hasPlayer)
        assertFalse(s.media.isPlaying)
        assertEquals(60f, s.media.positionSeconds(80_000)!!, 0.01f) // paused: doesn't advance
    }

    @Test
    fun `player switch and nothing playing`() {
        var s = spotifyPlaying()
            .onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.NAME, "Music"), 2_000)
            .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.TITLE, "Otra"), 2_000)
        assertEquals("Music", s.media.playerName)
        assertEquals("Otra", s.media.title)

        s = s.onEntityUpdate(eu(Entity.PLAYER, PlayerAttribute.NAME, ""), 3_000)
        assertFalse(s.hasPlayer)
    }

    @Test
    fun `unsubscribing drops stale values`() {
        val s = spotifyPlaying().onStatus(available = true, subscribed = false)
        assertFalse(s.hasPlayer)
        assertNull(s.media.title)
        assertTrue(s.available)
    }

    @Test
    fun `truncated title replaced by the full value`() {
        val s = spotifyPlaying()
            .onEntityUpdate(eu(Entity.TRACK, TrackAttribute.TITLE, "Un título muy la", flags = 1), 5_000)
            .onFullValue(Entity.TRACK, TrackAttribute.TITLE, "Un título muy largo de verdad", 5_100)
        assertEquals("Un título muy largo de verdad", s.media.title)
        assertTrue(s.media.truncated.isEmpty())
    }

    @Test
    fun `commands offered follow the supported list`() {
        val s = spotifyPlaying().onSupportedCommands(byteArrayOf(2, 3, 4))
        assertTrue(s.supports(RemoteCommand.TOGGLE_PLAY_PAUSE))
        assertFalse(s.supports(RemoteCommand.VOLUME_UP))
        // Before the iPhone says anything, offer everything
        assertTrue(MusicSession.NONE.supports(RemoteCommand.VOLUME_UP))
    }
}
