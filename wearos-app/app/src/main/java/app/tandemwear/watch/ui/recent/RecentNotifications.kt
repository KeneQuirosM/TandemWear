package app.tandemwear.watch.ui.recent

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ancs.AppIconMapper
import app.tandemwear.watch.data.history.HistoryEntry
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType

/** "5 min ago", localized by the system. */
fun relativeTime(postedAt: Long, nowMs: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(postedAt, nowMs, DateUtils.MINUTE_IN_MILLIS).toString()

/** Title and text shown for [entry], with the hidden-content placeholder applied. */
@Composable
fun entryTexts(entry: HistoryEntry): Pair<String, String> =
    if (entry.contentHidden) {
        entry.appName to stringResource(R.string.notification_hidden_text, entry.appName)
    } else {
        entry.title to entry.message
    }

/** One row of the recent list: app icon, app name, time, sender and a 2-line preview. */
@Composable
fun NotificationCard(entry: HistoryEntry, onClick: () -> Unit) {
    val (title, message) = entryTexts(entry)
    val time = relativeTime(entry.postedAt)
    val spoken = stringResource(R.string.recent_item_description, entry.appName, title, message, time)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                painterResource(AppIconMapper.getIconResId(entry.appId, entry.categoryId)),
                contentDescription = null,
                tint = TandemColors.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                entry.appName,
                fontSize = TandemType.caption,
                color = TandemColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(time, fontSize = TandemType.caption, color = TandemColors.textMuted, maxLines = 1)
        }
        Text(
            title,
            fontSize = TandemType.title,
            color = TandemColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (message.isNotEmpty()) {
            Text(
                message,
                fontSize = TandemType.body,
                color = TandemColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyRecent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            stringResource(R.string.recent_empty),
            fontSize = TandemType.body,
            color = TandemColors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}
