package app.tandemwear.watch.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ui.components.ScreenTitle
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    connected: Boolean,
    onApps: () -> Unit,
    onQuietHours: (() -> Unit)?,
    onPrivacy: (() -> Unit)?,
    onClearNotifications: () -> Unit,
    onDisconnect: () -> Unit,
    onConversations: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()

    TandemList {
        item { ScreenTitle(stringResource(R.string.settings_title)) }
        item { NavButton(Icons.Rounded.Apps, stringResource(R.string.settings_apps), null, onApps) }
        item {
            NavButton(
                Icons.Rounded.Vibration,
                stringResource(R.string.settings_sound_vibration),
                stringResource(R.string.settings_sound_vibration_desc)
            ) { viewModel.openSystemNotificationSettings() }
        }
        item {
            NavButton(
                Icons.Rounded.Forum,
                stringResource(R.string.settings_conversations),
                stringResource(
                    R.string.settings_conversations_desc,
                    settings.vips.size,
                    settings.mutedConversations.count { it.value > System.currentTimeMillis() }
                ),
                onConversations
            )
        }
        if (onQuietHours != null) {
            item {
                NavButton(
                    Icons.Rounded.Bedtime,
                    stringResource(R.string.settings_quiet_hours),
                    stringResource(if (settings.quietHours.enabled) R.string.state_on else R.string.state_off),
                    onQuietHours
                )
            }
        }
        item {
            SwitchButton(
                checked = settings.musicEnabled,
                onCheckedChange = { viewModel.setMusicEnabled(it) },
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                secondaryLabel = { Text(stringResource(R.string.settings_music_desc), fontSize = TandemType.caption) },
                label = { Text(stringResource(R.string.settings_music), fontSize = TandemType.label) }
            )
        }
        if (onPrivacy != null) {
            item { NavButton(Icons.Rounded.Lock, stringResource(R.string.settings_privacy), null, onPrivacy) }
        }
        item {
            SwitchButton(
                checked = settings.historyEnabled,
                onCheckedChange = { viewModel.setHistoryEnabled(it) },
                modifier = Modifier.fillMaxWidth(),
                secondaryLabel = { Text(stringResource(R.string.settings_history_desc), fontSize = TandemType.caption) },
                label = { Text(stringResource(R.string.settings_history), fontSize = TandemType.label) }
            )
        }
        item {
            OutlinedButton(
                onClick = { viewModel.clearHistory() },
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                label = { Text(stringResource(R.string.settings_clear_history), fontSize = TandemType.label) }
            )
        }
        if (connected) {
            item {
                OutlinedButton(
                    onClick = onClearNotifications,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.action_clear_notifications), fontSize = TandemType.label) }
                )
            }
            item {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = TandemColors.stateAttention) },
                    label = {
                        Text(stringResource(R.string.action_disconnect), fontSize = TandemType.label, color = TandemColors.stateAttention)
                    }
                )
            }
        }
    }
}

@Composable
fun NavButton(icon: ImageVector, label: String, secondary: String?, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(icon, contentDescription = null) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, fontSize = TandemType.caption) }
        } else {
            null
        },
        label = { Text(label, fontSize = TandemType.label) }
    )
}
