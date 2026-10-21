package app.tandemwear.watch.ui.music

import app.tandemwear.watch.ams.MusicSession
import app.tandemwear.watch.ams.RemoteCommand

/**
 * What the player screen shows. Pure: derived from [MusicSession] and a clock, so the
 * mapping and the progress maths are unit-tested.
 */
sealed class PlayerModel {
    /** "Música" is off in Settings. */
    data object MusicOff : PlayerModel()
    /** No ANCS session, or the iPhone doesn't publish AMS on this link. */
    data object Unavailable : PlayerModel()
    /** Connected and subscribed, but no app is playing ("Nada suena"). */
    data object NothingPlaying : PlayerModel()

    data class Playing(
        val appName: String?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val isPlaying: Boolean,
        val positionSeconds: Float?,
        val durationSeconds: Float?,
        /** 0..1, null when unknown. */
        val volume: Float?,
        val canTogglePlay: Boolean,
        val canPrevious: Boolean,
        val canNext: Boolean,
        val canVolume: Boolean
    ) : PlayerModel() {
        /** 0..1 or null when the duration is unknown. */
        val progress: Float?
            get() {
                val d = durationSeconds ?: return null
                val p = positionSeconds ?: return null
                return if (d > 0f) (p / d).coerceIn(0f, 1f) else null
            }
    }

    companion object {
        fun from(music: MusicSession, musicEnabled: Boolean, nowMs: Long): PlayerModel {
            if (!musicEnabled) return MusicOff
            if (!music.available || !music.subscribed) return Unavailable
            if (!music.hasPlayer) return NothingPlaying
            val m = music.media
            return Playing(
                appName = m.playerName,
                title = m.title,
                artist = m.artist,
                album = m.album,
                isPlaying = m.isPlaying,
                positionSeconds = m.positionSeconds(nowMs),
                durationSeconds = m.durationSeconds,
                volume = m.volume,
                canTogglePlay = music.supports(RemoteCommand.TOGGLE_PLAY_PAUSE) ||
                    music.supports(RemoteCommand.PLAY) || music.supports(RemoteCommand.PAUSE),
                canPrevious = music.supports(RemoteCommand.PREVIOUS_TRACK),
                canNext = music.supports(RemoteCommand.NEXT_TRACK),
                canVolume = music.supports(RemoteCommand.VOLUME_UP) && music.supports(RemoteCommand.VOLUME_DOWN)
            )
        }

        /** Toggle if supported, else the explicit command for the current state. */
        fun playPauseCommand(music: MusicSession): RemoteCommand = when {
            music.supports(RemoteCommand.TOGGLE_PLAY_PAUSE) -> RemoteCommand.TOGGLE_PLAY_PAUSE
            music.media.isPlaying -> RemoteCommand.PAUSE
            else -> RemoteCommand.PLAY
        }

        /** "3:07", "1:02:05"; null for unknown. */
        fun formatTime(seconds: Float?): String? {
            if (seconds == null || seconds.isNaN() || seconds < 0f) return null
            val total = seconds.toInt()
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
