package app.tandemwear.watch.ui.recent

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.notify.MuteDuration
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the detail screen needs to offer VIP / silence for one conversation. */
data class ConversationUi(
    val isVip: Boolean,
    /** End of the silence (ms since epoch), or null when not silenced. */
    val mutedUntil: Long?
)

/** "Marcar como VIP" and "Silenciar esta conversación" (15 min, 1 h, until 07:00), no typing. */
fun ScalingLazyListScope.conversationActions(
    state: ConversationUi,
    onVip: (Boolean) -> Unit,
    onMute: (MuteDuration) -> Unit,
    onUnmute: () -> Unit
) {
    item {
        FilledTonalButton(
            onClick = { onVip(!state.isVip) },
            modifier = Modifier.fillMaxWidth(),
            icon = {
                Icon(
                    if (state.isVip) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = TandemColors.primary
                )
            },
            label = {
                Text(
                    stringResource(if (state.isVip) R.string.conversation_vip_remove else R.string.conversation_vip_add),
                    fontSize = TandemType.label
                )
            }
        )
    }
    val until = state.mutedUntil
    if (until != null) {
        item {
            OutlinedButton(
                onClick = onUnmute,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                label = { Text(stringResource(R.string.conversation_unmute), fontSize = TandemType.label) },
                secondaryLabel = { Text(mutedUntilText(until), fontSize = TandemType.caption) }
            )
        }
    } else {
        item {
            ListHeader(modifier = Modifier.semantics { heading() }) {
                Text(stringResource(R.string.conversation_mute_title), fontSize = TandemType.label)
            }
        }
        for ((duration, label) in listOf(
            MuteDuration.MINUTES_15 to R.string.conversation_mute_15m,
            MuteDuration.HOUR_1 to R.string.conversation_mute_1h,
            MuteDuration.UNTIL_TOMORROW_MORNING to R.string.conversation_mute_tomorrow
        )) {
            item {
                OutlinedButton(
                    onClick = { onMute(duration) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
                    label = { Text(stringResource(label), fontSize = TandemType.label) }
                )
            }
        }
    }
}

/** "Silenciada hasta 21:45" / "hasta mañana 07:00", in the watch's 12/24 h format. */
@Composable
fun mutedUntilText(untilMs: Long): String {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val time = DateFormat.getTimeFormat(context).format(java.util.Date(untilMs))
    val day = Instant.ofEpochMilli(untilMs).atZone(zone).toLocalDate()
    return if (day == LocalDate.now(zone)) {
        stringResource(R.string.conversation_muted_until, time)
    } else {
        stringResource(R.string.conversation_muted_until_tomorrow, time)
    }
}
