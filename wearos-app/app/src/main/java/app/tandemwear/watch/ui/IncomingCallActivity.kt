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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.R
import app.tandemwear.watch.ancs.AncsService
import app.tandemwear.watch.ancs.NotificationActionReceiver
import app.tandemwear.watch.ble.AncsConstants
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
        const val ACTION_CALL_ENDED = "app.tandemwear.watch.CALL_ENDED"
        const val ACTION_CALL_ANSWERED = "app.tandemwear.watch.CALL_ANSWERED"
        const val ACTION_CALLER_NAME_UPDATED = "app.tandemwear.watch.CALLER_NAME_UPDATED"

        /** Auto-dismiss after this many ms if no ANCS event arrives */
        private const val AUTO_DISMISS_TIMEOUT_MS = 45_000L
        /** After call is answered on iPhone, auto-dismiss after this many ms */
        private const val ACTIVE_CALL_DISMISS_MS = 5_000L
    }

    private var notificationUid: Long = -1
    private var callerNameState = mutableStateOf("Unknown Caller")
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

        callerNameState.value = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown Caller"
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Phone"
        notificationUid = intent.getLongExtra(EXTRA_NOTIFICATION_UID, -1)

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
                            performAction(AncsConstants.ACTION_POSITIVE)
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: IllegalArgumentException) { }
    }

    private fun performAction(actionId: Int) {
        if (notificationUid == -1L) return
        val intent = Intent(this, AncsService::class.java).apply {
            action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notificationUid)
            putExtra(AncsService.EXTRA_ACTION_ID, actionId)
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
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = callerName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Incoming Call",
            fontSize = 12.sp,
            color = Color.Gray,
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
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444)
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_decline),
                    contentDescription = "Decline",
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Answer button (green) — Lucide phone
            Button(
                onClick = onAnswer,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF22C55E)
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_answer),
                    contentDescription = "Answer",
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
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
            tint = Color(0xFF22C55E)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = callerName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Active on iPhone",
            fontSize = 12.sp,
            color = Color(0xFF22C55E),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF374151)
            )
        ) {
            Text("Dismiss", fontSize = 13.sp)
        }
    }
}
