package app.tandemwear.watch.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ams.MusicSession
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType

/** Home screen entry to the player: app, state, title and artist. */
@Composable
fun MusicCard(music: MusicSession, onClick: () -> Unit) {
    val m = music.media
    val title = m.title ?: stringResource(R.string.player_unknown_title)
    val state = stringResource(if (m.isPlaying) R.string.player_state_playing else R.string.player_state_paused)
    val spoken = stringResource(R.string.music_card_description, state, title, m.artist ?: "", m.playerName ?: "")
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                if (m.isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.Pause,
                contentDescription = null,
                tint = TandemColors.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                listOfNotNull(state, m.playerName).joinToString(" · "),
                fontSize = TandemType.caption,
                color = TandemColors.textMuted,
                maxLines = 1
            )
        }
        Text(title, fontSize = TandemType.title, color = TandemColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        m.artist?.let {
            Text(it, fontSize = TandemType.body, color = TandemColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
