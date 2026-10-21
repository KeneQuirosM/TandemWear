package app.tandemwear.watch.ui.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.model.ConnectionState
import app.tandemwear.watch.ui.components.ConnectionMark
import app.tandemwear.watch.ui.components.ScreenTitle
import app.tandemwear.watch.ui.components.TandemList
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.viewmodel.MainViewModel

/**
 * Pairing: same flow as before (START_PAIRING makes the watch visible; the user taps it in
 * iPhone › Settings › Bluetooth). Only the look changed. Leaves when the session is up.
 */
@Composable
fun PairScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()

    // Opening this screen makes the watch visible to the iPhone
    LaunchedEffect(Unit) { viewModel.startPairing() }

    // Go back when the ANCS session is up
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) onDone()
    }

    val state = connectionState
    val failed = state is ConnectionState.Error || state is ConnectionState.RepairNeeded

    TandemList {
        item { ScreenTitle(stringResource(R.string.pair_title)) }

        item {
            Text(
                stringResource(R.string.pair_instructions, viewModel.watchName),
                textAlign = TextAlign.Center,
                fontSize = TandemType.body,
                color = TandemColors.textPrimary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        item {
            val status = when (state) {
                is ConnectionState.WaitingForIphone -> stringResource(R.string.pair_status_visible)
                is ConnectionState.Connecting -> stringResource(R.string.pair_status_connecting)
                is ConnectionState.Bonding -> stringResource(R.string.pair_status_bonding)
                is ConnectionState.LinkedWithoutAncs -> stringResource(R.string.pair_status_linked_no_ancs)
                is ConnectionState.Error -> stringResource(R.string.status_error_generic) + "\n" + state.message
                is ConnectionState.RepairNeeded -> stringResource(R.string.repair_title)
                else -> stringResource(R.string.pair_status_starting)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                if (failed) {
                    ConnectionMark(color = TandemColors.stateAttention, linked = false, size = 28.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
                Text(
                    status,
                    textAlign = TextAlign.Center,
                    fontSize = TandemType.label,
                    color = if (failed) TandemColors.stateAttention else TandemColors.textMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item {
            Text(
                stringResource(R.string.pair_share_notifications),
                textAlign = TextAlign.Center,
                fontSize = TandemType.caption,
                color = TandemColors.textMuted,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (failed) {
            item {
                Button(
                    onClick = { viewModel.startPairing() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.action_try_again), fontSize = TandemType.label) }
                )
            }
        }
    }
}
