package app.tandemwear.watch.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.model.ConnectionState
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.viewmodel.MainViewModel

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var showPairScreen by remember { mutableStateOf(false) }

    if (showPairScreen) {
        PairNewDeviceScreen(
            viewModel = viewModel,
            onDismiss = { showPairScreen = false }
        )
    } else {
        HomeScreen(
            viewModel = viewModel,
            onPairNewDevice = { showPairScreen = true }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(viewModel: MainViewModel, onPairNewDevice: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val defaultPhoneName = stringResource(R.string.default_phone_name)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader(modifier = Modifier.semantics { heading() }) {
                Text(stringResource(R.string.app_name))
            }
        }

        when (val state = connectionState) {
            is ConnectionState.Connected -> {
                val deviceName = state.deviceName ?: defaultPhoneName

                // Status
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = TandemColors.success,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.status_connected), fontSize = TandemType.title)
                            Text(
                                stringResource(R.string.status_notifications_active),
                                fontSize = TandemType.caption,
                                color = TandemColors.success
                            )
                        }
                    }
                }

                // iPhone info
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PhoneIphone,
                            contentDescription = null,
                            tint = TandemColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(deviceName, fontSize = TandemType.title)
                            Text(
                                stringResource(R.string.status_via_ancs),
                                fontSize = TandemType.caption,
                                color = TandemColors.textMuted
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Clear notifications
                item {
                    Button(
                        onClick = { viewModel.clearAllNotifications() },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(stringResource(R.string.action_clear_notifications), fontSize = TandemType.label)
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // Disconnect
                item {
                    Button(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TandemColors.decline
                        )
                    ) {
                        Text(
                            stringResource(R.string.action_disconnect),
                            fontSize = TandemType.label,
                            color = TandemColors.onDecline
                        )
                    }
                }
            }

            is ConnectionState.Connecting, is ConnectionState.Bonding -> {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (state is ConnectionState.Bonding) {
                                stringResource(R.string.status_pairing_with, state.deviceName ?: defaultPhoneName)
                            } else {
                                stringResource(
                                    R.string.status_connecting_to,
                                    (state as ConnectionState.Connecting).deviceName ?: defaultPhoneName
                                )
                            },
                            textAlign = TextAlign.Center,
                            fontSize = TandemType.body
                        )
                    }
                }
            }

            else -> {
                // Idle / Disconnected / Error / RepairNeeded — show reconnect or pair
                val repairNeeded = state is ConnectionState.RepairNeeded

                if (repairNeeded) {
                    item {
                        StatusBlock(
                            iconTint = TandemColors.warning,
                            title = stringResource(R.string.repair_title),
                            body = stringResource(R.string.repair_body)
                        )
                    }
                } else if (viewModel.hasPairedIPhone()) {
                    // Has a bonded device — show reconnecting state
                    item {
                        if (state is ConnectionState.Error) {
                            StatusBlock(
                                iconTint = TandemColors.textMuted,
                                title = stringResource(R.string.status_error_generic),
                                body = state.message
                            )
                        } else {
                            StatusBlock(
                                iconTint = TandemColors.textMuted,
                                title = stringResource(R.string.status_not_in_range),
                                body = null
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.startService() },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text(stringResource(R.string.action_reconnect), fontSize = TandemType.label)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(4.dp)) }
                } else {
                    // No bonded device — prompt to pair
                    item {
                        StatusBlock(
                            iconTint = TandemColors.textMuted,
                            title = stringResource(R.string.no_iphone_title),
                            body = stringResource(R.string.no_iphone_body)
                        )
                    }
                }

                // Always show the Pair button
                item {
                    Button(
                        onClick = onPairNewDevice,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = if (viewModel.hasPairedIPhone() && !repairNeeded)
                            ButtonDefaults.buttonColors(containerColor = TandemColors.surfaceButton)
                        else ButtonDefaults.buttonColors()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_pair_iphone), fontSize = TandemType.label)
                        }
                    }
                }
            }
        }
    }
}

/** Icon + title + optional explanation, centered. */
@Composable
private fun StatusBlock(iconTint: androidx.compose.ui.graphics.Color, title: String, body: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            Icons.Rounded.PhoneIphone,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, textAlign = TextAlign.Center, fontSize = TandemType.title)
        if (body != null) {
            Text(
                body,
                textAlign = TextAlign.Center,
                fontSize = TandemType.caption,
                color = TandemColors.textMuted
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PairNewDeviceScreen(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()

    // Opening this screen makes the watch visible to the iPhone
    LaunchedEffect(Unit) { viewModel.startPairing() }

    // Auto-dismiss when the ANCS session is up
    if (connectionState is ConnectionState.Connected) {
        onDismiss()
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader(modifier = Modifier.semantics { heading() }) {
                Text(stringResource(R.string.pair_title))
            }
        }

        item {
            Text(
                stringResource(R.string.pair_instructions, viewModel.watchName),
                textAlign = TextAlign.Center,
                fontSize = TandemType.body,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item {
            val state = connectionState
            val showSpinner = state !is ConnectionState.Error && state !is ConnectionState.RepairNeeded
            val status = when (state) {
                is ConnectionState.WaitingForIphone -> stringResource(R.string.pair_status_visible)
                is ConnectionState.Connecting -> stringResource(R.string.pair_status_connecting)
                is ConnectionState.Bonding -> stringResource(R.string.pair_status_bonding)
                is ConnectionState.Error -> stringResource(R.string.status_error_generic) + "\n" + state.message
                is ConnectionState.RepairNeeded -> stringResource(R.string.repair_title)
                else -> stringResource(R.string.pair_status_starting)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    status,
                    textAlign = TextAlign.Center,
                    fontSize = TandemType.label,
                    color = TandemColors.textMuted
                )
            }
        }

        item {
            Text(
                stringResource(R.string.pair_share_notifications),
                textAlign = TextAlign.Center,
                fontSize = TandemType.caption,
                color = TandemColors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (connectionState is ConnectionState.Error || connectionState is ConnectionState.RepairNeeded) {
            item {
                Button(
                    onClick = { viewModel.startPairing() },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.action_try_again), fontSize = TandemType.label)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Back button
        item {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TandemColors.surfaceButton
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_back), fontSize = TandemType.label)
                }
            }
        }
    }
}
