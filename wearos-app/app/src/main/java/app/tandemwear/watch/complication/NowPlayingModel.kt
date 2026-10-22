package app.tandemwear.watch.complication

import app.tandemwear.watch.tile.TileModel

/**
 * Texts of the "Now playing" complication. Pure: no Android types, unit-tested.
 * SHORT_TEXT fits ≤ 7 characters, so it shows the play state symbol, not the title.
 */
data class NowPlayingModel(
    /** Something is the iPhone's now-playing app. */
    val active: Boolean,
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?
) {
    /** ▶ playing, ❚❚ paused, – nothing. */
    val shortText: String
        get() = when {
            !active -> NOTHING
            isPlaying -> PLAYING
            else -> PAUSED
        }

    /** "Title · Artist", or whichever exists; null when nothing plays (caller uses its own text). */
    val longText: String?
        get() {
            if (!active) return null
            val parts = listOfNotNull(title?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
            return parts.joinToString(" · ").ifEmpty { null }
        }

    companion object {
        const val PLAYING = "▶"
        const val PAUSED = "❚❚"
        const val NOTHING = "–"

        fun from(music: TileModel.Music?): NowPlayingModel =
            if (music == null) NowPlayingModel(active = false, isPlaying = false, title = null, artist = null)
            else NowPlayingModel(active = true, isPlaying = music.isPlaying, title = music.title, artist = music.artist)
    }
}
