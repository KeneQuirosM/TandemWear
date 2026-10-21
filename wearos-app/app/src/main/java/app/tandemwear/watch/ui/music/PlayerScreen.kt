package app.tandemwear.watch.ui.music

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ams.MusicSession
import app.tandemwear.watch.ams.RemoteCommand
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import kotlinx.coroutines.delay

/**
 * Player for whatever the iPhone is playing (AMS). The progress bar advances by itself
 * between AMS updates (Apple's formula), redrawn once a second while playing and on screen.
 */
@Composable
fun PlayerScreen(music: MusicSession, musicEnabled: Boolean, onCommand: (RemoteCommand) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(music.media.isPlaying, music.media.playbackInfoReceivedAtMs) {
        now = SystemClock.elapsedRealtime()
        while (music.media.isPlaying) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    PlayerContent(
        model = PlayerModel.from(music, musicEnabled, now),
        onCommand = { command ->
            onCommand(if (command == RemoteCommand.TOGGLE_PLAY_PAUSE) PlayerModel.playPauseCommand(music) else command)
        }
    )
}

/** Stateless player, also used by the previews. */
@Composable
fun PlayerContent(model: PlayerModel, onCommand: (RemoteCommand) -> Unit) {
    TandemList {
        when (model) {
            PlayerModel.MusicOff -> item {
                Message(Icons.Rounded.MusicOff, stringResource(R.string.player_music_off), stringResource(R.string.player_music_off_body))
            }
            PlayerModel.Unavailable -> item {
                Message(Icons.Rounded.MusicOff, stringResource(R.string.player_unavailable), stringResource(R.string.player_unavailable_body))
            }
            PlayerModel.NothingPlaying -> item {
                Message(Icons.Rounded.MusicNote, stringResource(R.string.player_nothing_playing), stringResource(R.string.player_nothing_playing_body))
            }
            is PlayerModel.Playing -> {
                item { TrackInfo(model) }
                item { Progress(model) }
                item { Controls(model, onCommand) }
                if (model.canVolume) item { Volume(model, onCommand) }
            }
        }
    }
}

@Composable
private fun Message(icon: ImageVector, title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TandemColors.stateIdle, modifier = Modifier.size(32.dp))
        Text(
            title,
            fontSize = TandemType.headline,
            color = TandemColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(body, fontSize = TandemType.caption, color = TandemColors.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TrackInfo(model: PlayerModel.Playing) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        model.appName?.let {
            Text(it, fontSize = TandemType.caption, color = TandemColors.primary, maxLines = 1)
        }
        Text(
            model.title ?: stringResource(R.string.player_unknown_title),
            fontSize = TandemType.title,
            color = TandemColors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
        model.artist?.let {
            Text(it, fontSize = TandemType.body, color = TandemColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Progress(model: PlayerModel.Playing) {
    val progress = model.progress
    val elapsed = PlayerModel.formatTime(model.positionSeconds)
    val total = PlayerModel.formatTime(model.durationSeconds)
    val spoken = if (elapsed != null && total != null) stringResource(R.string.player_progress_description, elapsed, total) else ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            val y = size.height / 2
            val stroke = size.height
            drawLine(TandemColors.surfaceButton, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            if (progress != null) {
                drawLine(TandemColors.primary, Offset(0f, y), Offset(size.width * progress, y), stroke, StrokeCap.Round)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(elapsed ?: "", fontSize = TandemType.caption, color = TandemColors.textMuted)
            Text(total ?: "", fontSize = TandemType.caption, color = TandemColors.textMuted)
        }
    }
}

@Composable
private fun Controls(model: PlayerModel.Playing, onCommand: (RemoteCommand) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        TonalButton(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), model.canPrevious) {
            onCommand(RemoteCommand.PREVIOUS_TRACK)
        }
        FilledIconButton(
            onClick = { onCommand(RemoteCommand.TOGGLE_PLAY_PAUSE) },
            enabled = model.canTogglePlay,
            modifier = Modifier.size(IconButtonDefaults.LargeButtonSize)
        ) {
            Icon(
                if (model.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (model.isPlaying) R.string.player_pause else R.string.player_play),
                modifier = Modifier.size(32.dp)
            )
        }
        TonalButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), model.canNext) {
            onCommand(RemoteCommand.NEXT_TRACK)
        }
    }
}

@Composable
private fun Volume(model: PlayerModel.Playing, onCommand: (RemoteCommand) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        TonalButton(Icons.AutoMirrored.Rounded.VolumeDown, stringResource(R.string.player_volume_down), true) {
            onCommand(RemoteCommand.VOLUME_DOWN)
        }
        Text(
            model.volume?.let { stringResource(R.string.player_volume_percent, (it * 100).toInt()) } ?: "",
            fontSize = TandemType.label,
            color = TandemColors.textSecondary
        )
        TonalButton(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.player_volume_up), true) {
            onCommand(RemoteCommand.VOLUME_UP)
        }
    }
}

@Composable
private fun TonalButton(icon: ImageVector, description: String, enabled: Boolean, size: Dp = IconButtonDefaults.DefaultButtonSize, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description)
    }
}
