package app.tandemwear.watch.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import app.tandemwear.watch.R
import app.tandemwear.watch.ui.components.ScreenTitle
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.viewmodel.SettingsViewModel
import java.time.LocalTime
import java.util.Calendar

/** Which end of the window the time picker edits. */
enum class QuietEdge { START, END }

/** Do-not-disturb window: notifications arrive silently; calls ring only with the exception on. */
@Composable
fun QuietHoursScreen(viewModel: SettingsViewModel, onEdit: (QuietEdge) -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val quiet = settings.quietHours

    TandemList {
        item { ScreenTitle(stringResource(R.string.settings_quiet_hours)) }
        item {
            SwitchButton(
                checked = quiet.enabled,
                onCheckedChange = { viewModel.setQuietHours(quiet.copy(enabled = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.quiet_scheduled), fontSize = TandemType.label) }
            )
        }
        item {
            NavButton(
                Icons.Rounded.Schedule,
                stringResource(R.string.quiet_start),
                formatMinute(quiet.startMinute)
            ) { onEdit(QuietEdge.START) }
        }
        item {
            NavButton(
                Icons.Rounded.Schedule,
                stringResource(R.string.quiet_end),
                formatMinute(quiet.endMinute)
            ) { onEdit(QuietEdge.END) }
        }
        item {
            SwitchButton(
                checked = quiet.allowCalls,
                onCheckedChange = { viewModel.setQuietHours(quiet.copy(allowCalls = it)) },
                modifier = Modifier.fillMaxWidth(),
                secondaryLabel = { Text(stringResource(R.string.quiet_allow_calls_desc), fontSize = TandemType.caption) },
                label = { Text(stringResource(R.string.quiet_allow_calls), fontSize = TandemType.label) }
            )
        }
        item {
            Text(
                stringResource(R.string.quiet_explanation),
                fontSize = TandemType.caption,
                color = TandemColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * Wear Compose Material 3 TimePicker: scrolling columns (crown or swipe), no keyboard.
 * Follows the watch's 12/24-hour setting.
 */
@Composable
fun QuietTimePickerScreen(viewModel: SettingsViewModel, edge: QuietEdge, onDone: () -> Unit) {
    val quiet = viewModel.settings.collectAsState().value.quietHours
    val minute = if (edge == QuietEdge.START) quiet.startMinute else quiet.endMinute
    TimePicker(
        initialTime = LocalTime.of(minute / 60, minute % 60),
        onTimePicked = { time ->
            val picked = time.hour * 60 + time.minute
            viewModel.setQuietHours(
                if (edge == QuietEdge.START) quiet.copy(startMinute = picked) else quiet.copy(endMinute = picked)
            )
            onDone()
        }
    )
}

/** "22:00" or "10:00 PM" depending on the watch's time format. */
@Composable
fun formatMinute(minuteOfDay: Int): String {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/** Lock-screen privacy (global) and a pointer to per-app text hiding. */
@Composable
fun PrivacyScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    TandemList {
        item { ScreenTitle(stringResource(R.string.settings_privacy)) }
        item {
            SwitchButton(
                checked = settings.hideContentOnLockScreen,
                onCheckedChange = { viewModel.setHideContentOnLockScreen(it) },
                modifier = Modifier.fillMaxWidth(),
                secondaryLabel = { Text(stringResource(R.string.privacy_lock_screen_desc), fontSize = TandemType.caption) },
                label = { Text(stringResource(R.string.privacy_lock_screen), fontSize = TandemType.label) }
            )
        }
        item {
            Text(
                stringResource(R.string.privacy_per_app_hint),
                fontSize = TandemType.caption,
                color = TandemColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

