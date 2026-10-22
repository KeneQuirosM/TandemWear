package app.tandemwear.watch.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ams.MusicSession
import app.tandemwear.watch.data.history.HistoryEntry
import app.tandemwear.watch.ui.music.MusicCard
import app.tandemwear.watch.ui.components.ConnectionMark
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.recent.EmptyRecent
import app.tandemwear.watch.ui.recent.NotificationCard
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onPair: () -> Unit,
    onAbout: () -> Unit,
    onSettings: () -> Unit,
    onOpenNotification: (Long) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val status by viewModel.homeStatus.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val music by viewModel.music.collectAsState()
    // Re-checked on every resume: the user may come back from the system screen
    var callScreenBlocked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        callScreenBlocked = !viewModel.canShowCallScreen()
        onPauseOrDispose { }
    }
    HomeContent(
        status = status,
        recent = recent,
        music = music,
        onOpenPlayer = onOpenPlayer,
        onOpenBluetooth = { viewModel.openBluetoothSettings() },
        onReconnect = { viewModel.startService() },
        onPair = onPair,
        onAbout = onAbout,
        onSettings = onSettings,
        onOpenNotification = onOpenNotification,
        callScreenBlocked = callScreenBlocked,
        onAllowCallScreen = { viewModel.openCallScreenSettings() }
    )
}

/** Stateless home, also used by the screenshot tests. */
@Composable
fun HomeContent(
    status: HomeStatus,
    recent: List<HistoryEntry>,
    onOpenBluetooth: () -> Unit,
    onReconnect: () -> Unit,
    onPair: () -> Unit,
    onAbout: () -> Unit,
    onSettings: () -> Unit,
    onOpenNotification: (Long) -> Unit,
    music: MusicSession = MusicSession.NONE,
    onOpenPlayer: () -> Unit = {},
    callScreenBlocked: Boolean = false,
    onAllowCallScreen: () -> Unit = {}
) {
    TandemList {
        item { StatusHeader(status) }

        item {
            StatusAction(
                status = status,
                onOpenBluetooth = onOpenBluetooth,
                onReconnect = onReconnect,
                onPair = onPair
            )
        }

        // Without the full-screen permission calls only show as a notification
        if (callScreenBlocked) {
            item { CallScreenWarning(onAllowCallScreen) }
        }

        // Music card only while an app is the iPhone's now-playing app
        if (music.hasPlayer) {
            item { MusicCard(music, onOpenPlayer) }
        }

        item { ListHeader(modifier = Modifier.semantics { heading() }) { Text(stringResource(R.string.recent_title), fontSize = TandemType.label) } }
        if (recent.isEmpty()) {
            item { EmptyRecent() }
        } else {
            items(recent, key = { it.id }) { entry ->
                NotificationCard(entry, onClick = { onOpenNotification(entry.id) })
            }
        }

        item {
            FilledTonalButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.settings_title), fontSize = TandemType.label) }
            )
        }

        item {
            OutlinedButton(
                onClick = onAbout,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                label = { Text(stringResource(R.string.about_title), fontSize = TandemType.label) }
            )
        }
    }
}

/** Mark + state title + detail. Announced by TalkBack when it changes. */
@Composable
fun StatusHeader(status: HomeStatus) {
    val defaultPhone = stringResource(R.string.default_phone_name)
    val (color, title, body) = when (status) {
        is HomeStatus.Connected -> Triple(
            TandemColors.stateConnected,
            stringResource(R.string.home_connected_to, status.phoneName ?: defaultPhone),
            stringResource(R.string.status_notifications_active)
        )
        is HomeStatus.LinkedNoNotifications -> Triple(
            TandemColors.stateReconnecting,
            stringResource(R.string.home_linked_no_ancs),
            stringResource(R.string.home_linked_no_ancs_body)
        )
        is HomeStatus.Reconnecting -> Triple(
            TandemColors.stateReconnecting,
            stringResource(R.string.home_reconnecting),
            stringResource(R.string.status_connecting_to, status.phoneName ?: defaultPhone)
        )
        is HomeStatus.Disconnected -> Triple(
            TandemColors.stateIdle,
            stringResource(R.string.home_disconnected),
            status.errorDetail?.let { stringResource(R.string.home_error_detail, it) }
                ?: stringResource(R.string.home_disconnected_body)
        )
        HomeStatus.RepairNeeded -> Triple(
            TandemColors.stateAttention,
            stringResource(R.string.repair_title),
            stringResource(R.string.repair_body)
        )
        HomeStatus.BluetoothOff -> Triple(
            TandemColors.stateAttention,
            stringResource(R.string.home_bluetooth_off),
            stringResource(R.string.home_bluetooth_off_body)
        )
        HomeStatus.NotPaired -> Triple(
            TandemColors.stateIdle,
            stringResource(R.string.no_iphone_title),
            stringResource(R.string.no_iphone_body)
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        ConnectionMark(color = color, linked = status is HomeStatus.Connected, size = 36.dp)
        Text(
            title,
            fontSize = TandemType.headline,
            textAlign = TextAlign.Center,
            color = TandemColors.textPrimary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(body, fontSize = TandemType.caption, textAlign = TextAlign.Center, color = TandemColors.textMuted)
    }
}

/** The single main action of each state (none when connected). */
@Composable
private fun StatusAction(
    status: HomeStatus,
    onOpenBluetooth: () -> Unit,
    onReconnect: () -> Unit,
    onPair: () -> Unit
) {
    val modifier = Modifier.fillMaxWidth()
    when (status.action) {
        HomeStatus.Action.NONE -> Unit
        HomeStatus.Action.OPEN_BLUETOOTH_SETTINGS -> PrimaryAction(
            onClick = onOpenBluetooth, icon = { Icon(Icons.Rounded.Bluetooth, contentDescription = null) },
            text = stringResource(R.string.action_open_bluetooth), modifier = modifier
        )
        HomeStatus.Action.RECONNECT -> Column {
            PrimaryAction(
                onClick = onReconnect, icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = stringResource(R.string.action_reconnect), modifier = modifier
            )
            FilledTonalButton(
                onClick = onPair,
                modifier = modifier.padding(top = 4.dp),
                label = { Text(stringResource(R.string.action_pair_iphone), fontSize = TandemType.label) }
            )
        }
        HomeStatus.Action.PAIR, HomeStatus.Action.REPAIR -> PrimaryAction(
            onClick = onPair, icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            text = stringResource(
                if (status.action == HomeStatus.Action.REPAIR) R.string.action_pair_again else R.string.action_pair_iphone
            ),
            modifier = modifier,
            container = if (status.action == HomeStatus.Action.REPAIR) TandemColors.stateAttention else null
        )
    }
}

@Composable
private fun PrimaryAction(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: String,
    modifier: Modifier,
    container: Color? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (container != null) {
            ButtonDefaults.buttonColors(containerColor = container, contentColor = TandemColors.onError, iconColor = TandemColors.onError)
        } else {
            ButtonDefaults.buttonColors()
        },
        icon = { icon() },
        label = { Text(text, fontSize = TandemType.label) }
    )
}

/** Calls can't open the full-screen call screen: one tap to the system permission. */
@Composable
private fun CallScreenWarning(onAllow: () -> Unit) {
    FilledTonalButton(
        onClick = onAllow,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(Icons.Rounded.Call, contentDescription = null, tint = TandemColors.stateAttention) },
        label = { Text(stringResource(R.string.call_screen_blocked_title), fontSize = TandemType.label) },
        secondaryLabel = { Text(stringResource(R.string.call_screen_blocked_body), fontSize = TandemType.caption) }
    )
}
