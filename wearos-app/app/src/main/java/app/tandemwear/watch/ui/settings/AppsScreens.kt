package app.tandemwear.watch.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ancs.AppIconMapper
import app.tandemwear.watch.data.history.SeenApp
import app.tandemwear.watch.data.settings.AppRule
import app.tandemwear.watch.ui.components.ScreenTitle
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.Flow

/** Apps seen so far, each with a one-line summary of its rule. */
@Composable
fun AppsScreen(viewModel: SettingsViewModel, onApp: (String) -> Unit) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()

    TandemList {
        item { ScreenTitle(stringResource(R.string.settings_apps)) }
        if (apps.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.apps_empty),
                    fontSize = TandemType.body,
                    color = TandemColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
        items(apps, key = { it.appId }) { app ->
            val rule = settings.ruleFor(app.appId)
            FilledTonalButton(
                onClick = { onApp(app.appId) },
                modifier = Modifier.fillMaxWidth(),
                icon = { AppIcon(app) },
                secondaryLabel = { Text(ruleSummary(rule), fontSize = TandemType.caption) },
                label = { Text(app.displayName, fontSize = TandemType.label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun ruleSummary(rule: AppRule): String = when {
    rule.disabled -> stringResource(R.string.app_rule_off)
    rule.muted && rule.hideContent -> stringResource(R.string.app_rule_muted_hidden)
    rule.muted -> stringResource(R.string.app_rule_muted)
    rule.hideContent -> stringResource(R.string.app_rule_hidden)
    else -> stringResource(R.string.app_rule_on)
}

@Composable
private fun AppIcon(app: SeenApp) {
    Icon(
        painterResource(AppIconMapper.getIconResId(app.appId, app.categoryId)),
        contentDescription = null,
        modifier = Modifier.size(24.dp)
    )
}

/** One app: show, mute ("Silenciar esta app"), hide text. Applied live. */
@Composable
fun AppSettingsScreen(viewModel: SettingsViewModel, appId: String, appFlow: Flow<SeenApp?>) {
    val app by appFlow.collectAsState(initial = null)
    val settings by viewModel.settings.collectAsState()
    val rule = settings.ruleFor(appId)
    val name = app?.displayName ?: appId

    TandemList {
        item { ScreenTitle(name) }
        item {
            SwitchButton(
                checked = !rule.disabled,
                onCheckedChange = { viewModel.setAppRule(appId, rule.copy(disabled = !it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.app_show_notifications), fontSize = TandemType.label) }
            )
        }
        item {
            SwitchButton(
                checked = rule.muted,
                onCheckedChange = { viewModel.setAppRule(appId, rule.copy(muted = it)) },
                enabled = !rule.disabled,
                modifier = Modifier.fillMaxWidth(),
                secondaryLabel = { Text(stringResource(R.string.app_mute_desc), fontSize = TandemType.caption) },
                label = { Text(stringResource(R.string.app_mute), fontSize = TandemType.label) }
            )
        }
        item {
            SwitchButton(
                checked = rule.hideContent,
                onCheckedChange = { viewModel.setAppRule(appId, rule.copy(hideContent = it)) },
                enabled = !rule.disabled,
                modifier = Modifier.fillMaxWidth(),
                secondaryLabel = { Text(stringResource(R.string.app_hide_desc), fontSize = TandemType.caption) },
                label = { Text(stringResource(R.string.app_hide), fontSize = TandemType.label) }
            )
        }
        item {
            // The raw bundle ID helps add the app to the known-apps table
            Text(
                appId,
                fontSize = TandemType.caption,
                color = TandemColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
