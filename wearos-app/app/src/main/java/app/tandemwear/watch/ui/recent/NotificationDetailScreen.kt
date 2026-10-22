package app.tandemwear.watch.ui.recent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ancs.AppIconMapper
import app.tandemwear.watch.data.history.HistoryEntry
import app.tandemwear.watch.notify.MuteDuration
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import kotlinx.coroutines.flow.Flow

/**
 * Full notification. The iPhone's own actions (e.g. "Mark as read", "Clear") are offered
 * only while the notification belongs to the current ANCS session; after a reconnect its UID
 * names something else on the iPhone, so only "Dismiss from watch" remains.
 */
@Composable
fun NotificationDetailScreen(
    entryFlow: Flow<HistoryEntry?>,
    currentSessionId: Long?,
    onAction: (HistoryEntry, positive: Boolean) -> Unit,
    onDismiss: (HistoryEntry) -> Unit,
    onGone: () -> Unit,
    /** VIP / silence state of this entry's conversation; null = not offered (no usable title). */
    conversationOf: (HistoryEntry) -> ConversationUi? = { null },
    onVip: (HistoryEntry, Boolean) -> Unit = { _, _ -> },
    onMute: (HistoryEntry, MuteDuration) -> Unit = { _, _ -> },
    onUnmute: (HistoryEntry) -> Unit = {}
) {
    val entry by entryFlow.collectAsState(initial = null)
    val seen = remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        // Removed on the iPhone (or dismissed) while open: go back
        if (entry != null) seen.value = true else if (seen.value) onGone()
    }

    val e = entry ?: return
    val (title, message) = entryTexts(e)
    val actionable = currentSessionId != null && e.sessionId == currentSessionId

    TandemList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(
                    painterResource(AppIconMapper.getIconResId(e.appId, e.categoryId)),
                    contentDescription = null,
                    tint = TandemColors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    e.appName + " · " + relativeTime(e.postedAt),
                    fontSize = TandemType.caption,
                    color = TandemColors.textMuted,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(
                    title,
                    fontSize = TandemType.headline,
                    color = TandemColors.textPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                if (message.isNotEmpty()) {
                    Text(message, fontSize = TandemType.body, color = TandemColors.textSecondary)
                }
            }
        }
        if (actionable && e.positiveLabel != null) {
            item {
                Button(
                    onClick = { onAction(e, true) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                    label = { Text(e.positiveLabel, fontSize = TandemType.label) }
                )
            }
        }
        if (actionable && e.negativeLabel != null) {
            item {
                FilledTonalButton(
                    onClick = { onAction(e, false) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                    label = { Text(e.negativeLabel, fontSize = TandemType.label) }
                )
            }
        }
        if (!actionable && (e.positiveLabel != null || e.negativeLabel != null)) {
            item {
                Text(
                    stringResource(R.string.detail_actions_expired),
                    fontSize = TandemType.caption,
                    color = TandemColors.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        conversationOf(e)?.let { state ->
            conversationActions(
                state,
                onVip = { onVip(e, it) },
                onMute = { onMute(e, it) },
                onUnmute = { onUnmute(e) }
            )
        }
        item {
            OutlinedButton(
                onClick = { onDismiss(e) },
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                label = { Text(stringResource(R.string.action_dismiss_from_watch), fontSize = TandemType.label) }
            )
        }
    }
}
