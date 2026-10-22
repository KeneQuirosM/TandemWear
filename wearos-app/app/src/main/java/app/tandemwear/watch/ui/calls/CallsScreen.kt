package app.tandemwear.watch.ui.calls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.foundation.lazy.items
import app.tandemwear.watch.R
import app.tandemwear.watch.calls.CallOutcome
import app.tandemwear.watch.data.history.CallEntry
import app.tandemwear.watch.ui.components.ScreenTitle
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.recent.relativeTime
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType

/**
 * Calls seen by the watch: missed, answered (on the iPhone or the watch) and declined from
 * the watch. No call-back: the watch can't place calls through the iPhone.
 */
@Composable
fun CallsScreen(calls: List<CallEntry>, historyEnabled: Boolean, onClear: () -> Unit) {
    TandemList {
        item { ScreenTitle(stringResource(R.string.calls_title)) }
        if (calls.isEmpty()) {
            item {
                Text(
                    stringResource(if (historyEnabled) R.string.calls_empty else R.string.calls_history_off),
                    fontSize = TandemType.caption,
                    color = TandemColors.textMuted,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }
        } else {
            items(calls, key = { it.id }) { call -> CallRow(call) }
            item {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                    label = { Text(stringResource(R.string.calls_clear), fontSize = TandemType.label) }
                )
            }
        }
    }
}

@Composable
private fun CallRow(call: CallEntry) {
    val outcome = call.callOutcome
    val (icon, label) = outcomeIconAndLabel(outcome)
    val tint = if (outcome == CallOutcome.MISSED) TandemColors.stateAttention else TandemColors.primary
    val detail = label + " · " + call.appName + " · " + relativeTime(call.at)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = call.caller + ", " + detail }
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(call.caller, fontSize = TandemType.label, color = TandemColors.textPrimary, maxLines = 1)
            Text(detail, fontSize = TandemType.caption, color = TandemColors.textMuted, maxLines = 2)
        }
    }
}

@Composable
private fun outcomeIconAndLabel(outcome: CallOutcome): Pair<ImageVector, String> = when (outcome) {
    CallOutcome.MISSED -> Icons.AutoMirrored.Rounded.CallMissed to stringResource(R.string.calls_missed)
    CallOutcome.ANSWERED -> Icons.AutoMirrored.Rounded.CallReceived to stringResource(R.string.calls_answered)
    CallOutcome.ANSWERED_ON_WATCH -> Icons.Rounded.Watch to stringResource(R.string.calls_answered_on_watch)
    CallOutcome.DECLINED_ON_WATCH -> Icons.Rounded.CallEnd to stringResource(R.string.calls_declined_on_watch)
}
