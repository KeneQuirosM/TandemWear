package app.tandemwear.watch.complication

import app.tandemwear.watch.tile.TileModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingModelTest {

    private fun music(title: String?, artist: String?, playing: Boolean = true) =
        TileModel.Music(appName = "Spotify", title = title, artist = artist, isPlaying = playing, canTogglePlay = true)

    @Test
    fun `nothing playing`() {
        val m = NowPlayingModel.from(null)
        assertEquals("–", m.shortText)
        assertNull(m.longText)
    }

    @Test
    fun `short text is the play state, within 7 characters`() {
        assertEquals("▶", NowPlayingModel.from(music("A", "B")).shortText)
        assertEquals("❚❚", NowPlayingModel.from(music("A", "B", playing = false)).shortText)
        listOf(NowPlayingModel.PLAYING, NowPlayingModel.PAUSED, NowPlayingModel.NOTHING).forEach {
            assert(it.length <= 7)
        }
    }

    @Test
    fun `long text joins title and artist`() {
        assertEquals("Labios compartidos · Maná", NowPlayingModel.from(music("Labios compartidos", "Maná")).longText)
        assertEquals("Podcast", NowPlayingModel.from(music("Podcast", null)).longText)
        assertEquals("Maná", NowPlayingModel.from(music(" ", "Maná")).longText)
        assertNull(NowPlayingModel.from(music(null, null)).longText)
    }
}
