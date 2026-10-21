package app.tandemwear.watch.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ui.components.ConnectionMark
import app.tandemwear.watch.ui.components.ScreenTitle
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType

@Composable
fun AboutScreen(version: String, onLicenses: () -> Unit) {
    TandemList {
        item { ScreenTitle(stringResource(R.string.about_title)) }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ConnectionMark(color = TandemColors.stateConnected, linked = true, size = 36.dp)
                Text(stringResource(R.string.app_name), fontSize = TandemType.headline, color = TandemColors.textPrimary)
                Text(
                    stringResource(R.string.about_version, version),
                    fontSize = TandemType.caption,
                    color = TandemColors.textMuted
                )
            }
        }
        item { Paragraph(stringResource(R.string.about_description)) }
        item { Paragraph(stringResource(R.string.about_based_on)) }
        item { Paragraph(stringResource(R.string.about_privacy)) }
        item {
            FilledTonalButton(
                onClick = onLicenses,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.about_licenses), fontSize = TandemType.label) }
            )
        }
    }
}

/** Open source notices for everything shipped in the APK. */
@Composable
fun LicensesScreen() {
    TandemList {
        item { ScreenTitle(stringResource(R.string.about_licenses)) }
        item { Notice(stringResource(R.string.license_tandemwear_title), stringResource(R.string.license_mit_body)) }
        item { Notice(stringResource(R.string.license_wearbridge_title), stringResource(R.string.license_mit_body)) }
        item { Notice(stringResource(R.string.license_androidx_title), stringResource(R.string.license_apache_body)) }
        item { Notice(stringResource(R.string.license_icons_title), stringResource(R.string.license_icons_body)) }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        fontSize = TandemType.body,
        textAlign = TextAlign.Center,
        color = TandemColors.textSecondary,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun Notice(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(title, fontSize = TandemType.title, color = TandemColors.textPrimary)
        Text(body, fontSize = TandemType.caption, color = TandemColors.textMuted)
    }
}
