package app.tandemwear.watch.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ui.components.ConnectionMark
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType

/**
 * Ambient (always-on) view: black, outlines and dim gray text only, nothing filled, so it
 * draws few pixels. With burn-in protection the content moves a few dp every update
 * ([shiftStep] changes once a minute).
 */
@Composable
fun AmbientScreen(status: HomeStatus, burnInProtection: Boolean, shiftStep: Int) {
    val (dx, dy) = if (burnInProtection) BURN_IN_OFFSETS[Math.floorMod(shiftStep, BURN_IN_OFFSETS.size)] else 0 to 0
    val defaultPhone = stringResource(R.string.default_phone_name)
    val title = when (status) {
        is HomeStatus.Connected -> stringResource(R.string.home_connected_to, status.phoneName ?: defaultPhone)
        is HomeStatus.Reconnecting -> stringResource(R.string.home_reconnecting)
        is HomeStatus.LinkedNoNotifications -> stringResource(R.string.home_linked_no_ancs)
        is HomeStatus.Disconnected -> stringResource(R.string.home_disconnected)
        HomeStatus.RepairNeeded -> stringResource(R.string.repair_title)
        HomeStatus.BluetoothOff -> stringResource(R.string.home_bluetooth_off)
        HomeStatus.NotPaired -> stringResource(R.string.no_iphone_title)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(TandemColors.background)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(dx.dp, dy.dp)
                .padding(24.dp)
        ) {
            ConnectionMark(color = TandemColors.stateIdle, linked = status is HomeStatus.Connected)
            Text(title, fontSize = TandemType.title, color = TandemColors.textMuted, textAlign = TextAlign.Center)
        }
    }
}

private val BURN_IN_OFFSETS = listOf(0 to 0, 6 to 0, 6 to 6, 0 to 6, -6 to 6, -6 to 0, -6 to -6, 0 to -6, 6 to -6)
