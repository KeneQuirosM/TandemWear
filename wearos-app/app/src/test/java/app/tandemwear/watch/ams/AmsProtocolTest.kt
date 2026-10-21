package app.tandemwear.watch.ams

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmsProtocolTest {

    private fun update(entity: Entity, attribute: Int, value: String, flags: Int = 0): ByteArray =
        byteArrayOf(entity.id.toByte(), attribute.toByte(), flags.toByte()) + value.toByteArray(Charsets.UTF_8)

    @Test
    fun `uuids match apple specification`() {
        assertEquals("89d3502b-0f36-433a-8ef4-c502ad55f8dc", AmsProtocol.SERVICE_UUID.toString())
        assertEquals("9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2", AmsProtocol.REMOTE_COMMAND_UUID.toString())
        assertEquals("2f7cabce-808d-411f-9a0c-bb92ba96c102", AmsProtocol.ENTITY_UPDATE_UUID.toString())
        assertEquals("c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7", AmsProtocol.ENTITY_ATTRIBUTE_UUID.toString())
    }

    @Test
    fun `command builders follow the byte layouts`() {
        assertArrayEquals(
            byteArrayOf(2, 0, 1, 2, 3),
            AmsProtocol.entityUpdateCommand(Entity.TRACK, listOf(0, 1, 2, 3))
        )
        assertArrayEquals(byteArrayOf(2), AmsProtocol.remoteCommand(RemoteCommand.TOGGLE_PLAY_PAUSE))
        assertArrayEquals(byteArrayOf(9), AmsProtocol.remoteCommand(RemoteCommand.SKIP_FORWARD))
        assertArrayEquals(byteArrayOf(2, 2), AmsProtocol.entityAttributeRequest(Entity.TRACK, TrackAttribute.TITLE))
        assertEquals(3, AmsProtocol.defaultSubscriptions().size)
    }

    @Test
    fun `entity update is parsed with utf8 value and truncated flag`() {
        val parsed = AmsProtocol.parseEntityUpdate(update(Entity.TRACK, TrackAttribute.TITLE, "Canción ñ", flags = 0x01))!!
        assertEquals(Entity.TRACK, parsed.entity)
        assertEquals(TrackAttribute.TITLE, parsed.attribute)
        assertTrue(parsed.truncated)
        assertEquals("Canción ñ", parsed.value)
    }

    @Test
    fun `malformed or unknown entity updates are rejected`() {
        assertNull(AmsProtocol.parseEntityUpdate(byteArrayOf(0, 1)))
        assertNull(AmsProtocol.parseEntityUpdate(byteArrayOf(7, 0, 0)))
        // Empty value is valid (attribute cleared)
        assertEquals("", AmsProtocol.parseEntityUpdate(byteArrayOf(0, 0, 0))!!.value)
    }

    @Test
    fun `supported commands ignore reserved ids`() {
        val commands = AmsProtocol.parseSupportedCommands(byteArrayOf(0, 1, 3, 4, 200.toByte()))
        assertEquals(
            setOf(RemoteCommand.PLAY, RemoteCommand.PAUSE, RemoteCommand.NEXT_TRACK, RemoteCommand.PREVIOUS_TRACK),
            commands
        )
    }

    @Test
    fun `playback info parses and extrapolates position`() {
        val info = PlaybackInfo.parse("1,1.0,30.5")!!
        assertEquals(PlaybackState.PLAYING, info.state)
        assertEquals(30.5f, info.elapsedSeconds, 0.001f)
        assertEquals(40.5f, info.elapsedAt(receivedAtMs = 1_000, nowMs = 11_000), 0.001f)

        val paused = PlaybackInfo.parse("0,0.0,12")!!
        assertEquals(12f, paused.elapsedAt(0, 60_000), 0.001f)

        assertNull(PlaybackInfo.parse("1,1.0"))
        assertNull(PlaybackInfo.parse("9,1.0,0"))
        assertNull(PlaybackInfo.parse("x,y,z"))
    }

    @Test
    fun `media state accumulates updates`() {
        var state = MediaState()
        val now = 5_000L
        listOf(
            update(Entity.PLAYER, PlayerAttribute.NAME, "Spotify"),
            update(Entity.PLAYER, PlayerAttribute.PLAYBACK_INFO, "1,1.0,10"),
            update(Entity.PLAYER, PlayerAttribute.VOLUME, "0.5"),
            update(Entity.QUEUE, QueueAttribute.INDEX, "3"),
            update(Entity.QUEUE, QueueAttribute.COUNT, "12"),
            update(Entity.QUEUE, QueueAttribute.SHUFFLE_MODE, "2"),
            update(Entity.QUEUE, QueueAttribute.REPEAT_MODE, "1"),
            update(Entity.TRACK, TrackAttribute.ARTIST, "Artista"),
            update(Entity.TRACK, TrackAttribute.ALBUM, "Álbum"),
            update(Entity.TRACK, TrackAttribute.TITLE, "Título largo", flags = 0x01),
            update(Entity.TRACK, TrackAttribute.DURATION, "200.0")
        ).forEach { state = state.apply(AmsProtocol.parseEntityUpdate(it)!!, now) }

        assertTrue(state.hasPlayer)
        assertTrue(state.isPlaying)
        assertEquals(0.5f, state.volume!!, 0.001f)
        assertEquals(3, state.queueIndex)
        assertEquals(12, state.queueCount)
        assertEquals(QueueMode.ALL, state.shuffleMode)
        assertEquals(QueueMode.ONE, state.repeatMode)
        assertEquals("Artista", state.artist)
        assertEquals(200f, state.durationSeconds!!, 0.001f)
        assertEquals(setOf(Entity.TRACK to TrackAttribute.TITLE), state.truncated)
        assertEquals(15f, state.positionSeconds(now + 5_000)!!, 0.001f)
        assertEquals(200f, state.positionSeconds(now + 10_000_000)!!, 0.001f)

        val full = state.applyFullValue(Entity.TRACK, TrackAttribute.TITLE, "Título largo completo", now)
        assertEquals("Título largo completo", full.title)
        assertTrue(full.truncated.isEmpty())
    }

    @Test
    fun `player cleared when no app is playing`() {
        var state = MediaState()
            .apply(AmsProtocol.parseEntityUpdate(update(Entity.PLAYER, PlayerAttribute.NAME, "Music"))!!, 0)
            .apply(AmsProtocol.parseEntityUpdate(update(Entity.PLAYER, PlayerAttribute.PLAYBACK_INFO, "1,1,0"))!!, 0)
        state = state
            .apply(AmsProtocol.parseEntityUpdate(update(Entity.PLAYER, PlayerAttribute.NAME, ""))!!, 1)
            .apply(AmsProtocol.parseEntityUpdate(update(Entity.PLAYER, PlayerAttribute.PLAYBACK_INFO, ""))!!, 1)
        assertFalse(state.hasPlayer)
        assertNull(state.playbackInfo)
        // A malformed PlaybackInfo keeps the previous state instead of wiping it
        val kept = MediaState(playbackInfo = PlaybackInfo(PlaybackState.PAUSED, 0f, 3f))
            .apply(EntityUpdate(Entity.PLAYER, PlayerAttribute.PLAYBACK_INFO, false, "garbage"), 0)
        assertEquals(3f, kept.playbackInfo!!.elapsedSeconds, 0.001f)
    }
}
