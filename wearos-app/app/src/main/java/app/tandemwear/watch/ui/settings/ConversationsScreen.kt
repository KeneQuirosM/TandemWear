package app.tandemwear.watch.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ancs.KnownApps
import app.tandemwear.watch.ui.components.ScreenTitle
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.recent.mutedUntilText
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.viewmodel.SettingsViewModel

/**
 * Settings › Conversations: VIP and silenced conversations (added from a notification's
 * detail). Tapping an entry removes it. VIPs skip TandemWear's quiet hours only, never the
 * system's Do Not Disturb (that needs an adb grant: docs/PRUEBAS_FASE5.md, advanced).
 */
@Composable
fun ConversationsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val now = System.currentTimeMillis()
    val muted = settings.mutedConversations.filterValues { it > now }.toList().sortedBy { it.second }

    TandemList {
        item { ScreenTitle(stringResource(R.string.settings_conversations)) }
        item {
            SwitchButton(
                checked = settings.vipRingsInQuietHours,
                onCheckedChange = { viewModel.setVipRingsInQuietHours(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_vip_quiet_hours), fontSize = TandemType.label) },
                secondaryLabel = { Text(stringResource(R.string.settings_vip_quiet_hours_desc), fontSize = TandemType.caption) }
            )
        }
        item {
            ListHeader(modifier = Modifier.semantics { heading() }) {
                Text(stringResource(R.string.settings_vip_list), fontSize = TandemType.label)
            }
        }
        if (settings.vips.isEmpty()) {
            item { Hint(stringResource(R.string.settings_vip_empty)) }
        } else {
            for (c in settings.vips) {
                item(key = "vip:" + c.encode()) {
                    FilledTonalButton(
                        onClick = { viewModel.setVip(c, false) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = TandemColors.primary) },
                        label = { Text(c.title, fontSize = TandemType.label, maxLines = 1) },
                        secondaryLabel = {
                            Text(
                                stringResource(R.string.settings_tap_to_remove, KnownApps.displayName(c.appId)),
                                fontSize = TandemType.caption
                            )
                        }
                    )
                }
            }
        }
        item {
            ListHeader(modifier = Modifier.semantics { heading() }) {
                Text(stringResource(R.string.settings_muted_list), fontSize = TandemType.label)
            }
        }
        if (muted.isEmpty()) {
            item { Hint(stringResource(R.string.settings_muted_empty)) }
        } else {
            for ((c, until) in muted) {
                item(key = "muted:" + c.encode()) {
                    FilledTonalButton(
                        onClick = { viewModel.unmute(c) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
                        label = { Text(c.title, fontSize = TandemType.label, maxLines = 1) },
                        secondaryLabel = {
                            Text(
                                KnownApps.displayName(c.appId) + " · " + mutedUntilText(until),
                                fontSize = TandemType.caption
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = TandemType.caption,
        color = TandemColors.textMuted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    )
}
