package app.tandemwear.watch.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ancs.AncsService
import app.tandemwear.watch.ancs.NotificationActionReceiver
import app.tandemwear.watch.ancs.NotificationIdRegistry
import app.tandemwear.watch.ble.AncsConstants
import app.tandemwear.watch.ui.theme.TandemColors
import app.tandemwear.watch.ui.theme.TandemType
import app.tandemwear.watch.ui.theme.TandemWearTheme

/**
 * Full-screen incoming call activity shown when ANCS reports an incoming call.
 * Shows caller name with Answer/Decline buttons using Lucide icons.
 * Auto-dismisses when ANCS sends a REMOVE event for the call notification.
 */
class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
        const val EXTRA_APP_NAME = "app_name"
        /** ANCS session of the call's UID; Answer is sent only if that session is still current. */
        const val EXTRA_SESSION_ID = "ancs_session_id"
        const val ACTION_CALL_ENDED = "app.tandemwear.watch.CALL_ENDED"
        const val ACTION_CALL_ANSWERED = "app.tandemwear.watch.CALL_ANSWERED"
        const val ACTION_CALLER_NAME_UPDATED = "app.tandemwear.watch.CALLER_NAME_UPDATED"

        /** Auto-dismiss after this many ms if no ANCS event arrives */
        private const val AUTO_DISMISS_TIMEOUT_MS = 45_000L
        /** After call is answered on iPhone, auto-dismiss after this many ms */
        private const val ACTIVE_CALL_DISMISS_MS = 5_000L

        /** The call screen is started and on screen (read by the direct-start fallback). */
        @Volatile var isVisible = false
            private set
        /** elapsedRealtime when the screen last became visible (measures the full-screen path). */
        @Volatile var visibleSinceElapsed = 0L
            private set
    }

    private var sessionId: Long = NotificationIdRegistry.NO_SESSION
    private var notificationUid: Long = -1
    private var callerNameState = mutableStateOf("")
    /** true = call was answered on iPhone, show "Active on iPhone" UI */
    private var callAnsweredOnPhone = mutableStateOf(false)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CALL_ENDED -> finish()
                ACTION_CALL_ANSWERED -> {
                    // Call was answered on iPhone — transition to "active call" UI
                    callAnsweredOnPhone.value = true
                    // Auto-dismiss after a few seconds
                    handler.postDelayed({ finish() }, ACTIVE_CALL_DISMISS_MS)
                }
                ACTION_CALLER_NAME_UPDATED -> {
                    val name = intent.getStringExtra(EXTRA_CALLER_NAME)
                    if (!name.isNullOrEmpty()) {
                        callerNameState.value = name
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Dismiss the keyguard so the call screen is interactive
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        // Keep screen on while call screen is showing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        callerNameState.value = intent.getStringExtra(EXTRA_CALLER_NAME) ?: getString(R.string.call_unknown_caller)
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: getString(R.string.call_default_app)
        notificationUid = intent.getLongExtra(EXTRA_NOTIFICATION_UID, -1)
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, NotificationIdRegistry.NO_SESSION)

        val filter = IntentFilter().apply {
            addAction(ACTION_CALL_ENDED)
            addAction(ACTION_CALL_ANSWERED)
            addAction(ACTION_CALLER_NAME_UPDATED)
        }
        registerReceiver(callEndedReceiver, filter, RECEIVER_NOT_EXPORTED)

        // Safety timeout: auto-dismiss if no ANCS REMOVE/MODIFIED arrives
        handler.postDelayed({ finish() }, AUTO_DISMISS_TIMEOUT_MS)

        setContent {
            TandemWearTheme {
                val callerName by callerNameState
                val answeredOnPhone by callAnsweredOnPhone

                if (answeredOnPhone) {
                    CallActiveOnPhoneScreen(
                        callerName = callerName,
                        onDismiss = { finish() }
                    )
                } else {
                    IncomingCallScreen(
                        callerName = callerName,
                        appName = appName,
                        onAnswer = {
                            performAction(AncsConstants.ACTION_POSITIVE, sessionId)
                            finish()
                        },
                        onDecline = {
                            performAction(AncsConstants.ACTION_NEGATIVE)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visibleSinceElapsed = android.os.SystemClock.elapsedRealtime()
        isVisible = true
    }

    override fun onStop() {
        isVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: IllegalArgumentException) { }
    }

    /** Decline keeps sending without a session (its behaviour is verified on the watch). */
    private fun performAction(actionId: Int, session: Long = NotificationIdRegistry.NO_SESSION) {
        if (notificationUid == -1L) return
        val intent = Intent(this, AncsService::class.java).apply {
            action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notificationUid)
            putExtra(AncsService.EXTRA_ACTION_ID, actionId)
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, session)
        }
        startService(intent)
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    appName: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = appName,
            fontSize = TandemType.label,
            color = TandemColors.textMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = callerName,
            fontSize = TandemType.callerName,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.call_incoming),
            fontSize = TandemType.label,
            color = TandemColors.textMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Decline / Answer row
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline button (red) — Lucide phone-off
            Button(
                onClick = onDecline,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TandemColors.decline
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_decline),
                    contentDescription = stringResource(R.string.call_decline),
                    modifier = Modifier.size(24.dp),
                    tint = TandemColors.onDecline
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Answer button (green) — Lucide phone
            Button(
                onClick = onAnswer,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TandemColors.accept
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_answer),
                    contentDescription = stringResource(R.string.call_answer),
                    modifier = Modifier.size(24.dp),
                    tint = TandemColors.onAccept
                )
            }
        }
    }
}

@Composable
fun CallActiveOnPhoneScreen(
    callerName: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_call_answer),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = TandemColors.success
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = callerName,
            fontSize = TandemType.callerName,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.call_active_on_iphone),
            fontSize = TandemType.label,
            color = TandemColors.success,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = TandemColors.surfaceButton
            )
        ) {
            Text(stringResource(R.string.action_dismiss), fontSize = TandemType.body)
        }
    }
}
