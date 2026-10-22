package app.tandemwear.watch.ui.music

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.min
import androidx.wear.compose.material3.ScreenScaffold
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
    if (model is PlayerModel.Playing) {
        PlayingLayout(model, onCommand)
        return
    }
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
            is PlayerModel.Playing -> Unit // handled above
        }
    }
}

/**
 * Playing: rows stacked and centred, each as wide as the round screen allows at its height
 * ([PlayerLayout]). With the normal font everything, volume included, is visible without
 * scrolling; when it can't fit (large font, small screen) the column scrolls and a chevron
 * at the bottom shows there is more below.
 */
@Composable
private fun PlayingLayout(model: PlayerModel.Playing, onCommand: (RemoteCommand) -> Unit) {
    val scroll = rememberScrollState()
    ScreenScaffold(scrollState = scroll, contentPadding = PaddingValues(0.dp)) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val diameter = min(maxWidth, maxHeight)
            val fontScale = LocalDensity.current.fontScale
            val hasArtist = !model.artist.isNullOrBlank()
            val plan = remember(diameter, fontScale, hasArtist, model.canVolume) {
                PlayerLayout.plan(diameter.value, fontScale, hasArtist, model.canVolume)
            }
            val v = plan.variant
            fun width(row: PlayerLayout.Row) = (plan.widths[row] ?: diameter.value * 0.7f).dp
            // Scrolling: keep the first row clear of the curved time text
            val edge = if (plan.fits) 0.dp else PlayerLayout.TIME_TEXT_RESERVE.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(v.gap.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .heightIn(min = maxHeight)
                    .padding(vertical = edge)
            ) {
                Title(model, Modifier.width(width(PlayerLayout.Row.TITLE)))
                if (hasArtist && !v.artistInTimeRow) {
                    Text(
                        model.artist.orEmpty(),
                        fontSize = TandemType.body,
                        color = TandemColors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(width(PlayerLayout.Row.ARTIST))
                    )
                }
                Progress(
                    model,
                    middle = if (v.artistInTimeRow) model.artist else null,
                    modifier = Modifier.width(width(PlayerLayout.Row.PROGRESS))
                )
                Controls(model, v, onCommand)
                if (model.canVolume) Volume(model, v, plan.volumeText, onCommand)
            }
            if (scroll.canScrollForward) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.player_more_below),
                    tint = TandemColors.textMuted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(20.dp)
                )
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
private fun Title(model: PlayerModel.Playing, modifier: Modifier) {
    Text(
        model.title ?: stringResource(R.string.player_unknown_title),
        fontSize = TandemType.title,
        color = TandemColors.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }
    )
}

/** Bar and times; [middle] (the artist, in the dense layout) sits between the two times. */
@Composable
private fun Progress(model: PlayerModel.Playing, middle: String?, modifier: Modifier) {
    val progress = model.progress
    val elapsed = PlayerModel.formatTime(model.positionSeconds)
    val total = PlayerModel.formatTime(model.durationSeconds)
    val spoken = if (elapsed != null && total != null) stringResource(R.string.player_progress_description, elapsed, total) else ""
    Column(
        modifier = modifier
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Text(elapsed ?: "", fontSize = TandemType.caption, color = TandemColors.textMuted, maxLines = 1)
            Text(
                middle.orEmpty(),
                fontSize = TandemType.caption,
                color = TandemColors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(total ?: "", fontSize = TandemType.caption, color = TandemColors.textMuted, maxLines = 1)
        }
    }
}

@Composable
private fun Controls(model: PlayerModel.Playing, v: PlayerLayout.Variant, onCommand: (RemoteCommand) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        TonalButton(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), model.canPrevious, v.sideSize.dp) {
            onCommand(RemoteCommand.PREVIOUS_TRACK)
        }
        FilledIconButton(
            onClick = { onCommand(RemoteCommand.TOGGLE_PLAY_PAUSE) },
            enabled = model.canTogglePlay,
            modifier = Modifier.size(v.playSize.dp)
        ) {
            Icon(
                if (model.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (model.isPlaying) R.string.player_pause else R.string.player_play),
                modifier = Modifier.size((v.playSize * 0.55f).dp)
            )
        }
        TonalButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), model.canNext, v.sideSize.dp) {
            onCommand(RemoteCommand.NEXT_TRACK)
        }
    }
}

/**
 * Volume −/+ with the level between them ("60%") when it fits; otherwise the level is only
 * read out by TalkBack with each button.
 */
@Composable
private fun Volume(model: PlayerModel.Playing, v: PlayerLayout.Variant, showText: Boolean, onCommand: (RemoteCommand) -> Unit) {
    val level = model.volume?.let { stringResource(R.string.player_volume_percent, (it * 100).toInt()) }
    fun described(action: String) = if (level != null && !showText) "$action, $level" else action
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        TonalButton(
            Icons.AutoMirrored.Rounded.VolumeDown, described(stringResource(R.string.player_volume_down)), true, v.volumeButtonSize.dp
        ) { onCommand(RemoteCommand.VOLUME_DOWN) }
        if (showText && model.volume != null) {
            Text(
                stringResource(R.string.player_volume_short, (model.volume * 100).toInt()),
                fontSize = TandemType.label,
                color = TandemColors.textSecondary,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = level.orEmpty() }
            )
        }
        TonalButton(
            Icons.AutoMirrored.Rounded.VolumeUp, described(stringResource(R.string.player_volume_up)), true, v.volumeButtonSize.dp
        ) { onCommand(RemoteCommand.VOLUME_UP) }
    }
}

@Composable
private fun TonalButton(icon: ImageVector, description: String, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(size * 0.5f))
    }
}
