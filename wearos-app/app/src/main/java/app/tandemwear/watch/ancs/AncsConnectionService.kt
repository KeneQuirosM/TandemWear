package app.tandemwear.watch.ancs

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import app.tandemwear.watch.R
import app.tandemwear.watch.diag.DiagnosticsLog

/**
 * ConnectionService that integrates ANCS incoming calls with Android's Telecom framework.
 *
 * By registering as a self-managed ConnectionService, the system treats our
 * calls like native VoIP calls — the call UI shows over the charging screen,
 * lock screen, and gets full system priority.
 */
@SuppressLint("MissingPermission")
class AncsConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "AncsConnectionService"
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_SESSION_ID = "ancs_session_id"

        /** elapsedRealtime of the last report, to log how long Telecom takes. */
        @Volatile private var reportedAtElapsed = 0L
        private fun sinceReport() = SystemClock.elapsedRealtime() - reportedAtElapsed

        // Track the active call connection so AncsService can update/end it
        var activeConnection: AncsCallConnection? = null
            private set

        fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
            return PhoneAccountHandle(
                ComponentName(context, AncsConnectionService::class.java),
                "ancs_bridge_account"
            )
        }

        /**
         * Register the PhoneAccount with TelecomManager. Must be called once
         * (e.g., on app startup or service start).
         */
        fun registerPhoneAccount(context: Context) {
            val handle = getPhoneAccountHandle(context)
            val account = PhoneAccount.builder(handle, context.getString(R.string.app_name))
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build()

            val telecomManager = context.getSystemService(TelecomManager::class.java)
            telecomManager.registerPhoneAccount(account)
            Log.i(TAG, "Registered PhoneAccount for TandemWear")
        }

        /**
         * Report an incoming call to the Telecom framework.
         * The system will show its native call UI (works over charging screen).
         */
        fun reportIncomingCall(
            context: Context,
            notificationUid: Long,
            callerName: String,
            appName: String,
            sessionId: Long
        ) {
            val handle = getPhoneAccountHandle(context)
            reportedAtElapsed = SystemClock.elapsedRealtime()
            val extras = Bundle().apply {
                putLong(EXTRA_NOTIFICATION_UID, notificationUid)
                putLong(EXTRA_SESSION_ID, sessionId)
                putString(EXTRA_CALLER_NAME, callerName)
                putString(EXTRA_APP_NAME, appName)
            }

            val telecomManager = context.getSystemService(TelecomManager::class.java)
            try {
                telecomManager.addNewIncomingCall(handle, extras)
                Log.i(TAG, "Reported incoming call to Telecom: '$callerName' uid=$notificationUid")
            } catch (e: Exception) {
                DiagnosticsLog.log("telecom: addNewIncomingCall failed — ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        /**
         * End the active call (e.g., when ANCS sends REMOVE event).
         */
        fun endActiveCall() {
            activeConnection?.endCall()
            activeConnection = null
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val extras = request?.extras ?: Bundle()
        val uid = extras.getLong(EXTRA_NOTIFICATION_UID, -1)
        val callerName = extras.getString(EXTRA_CALLER_NAME, getString(R.string.call_incoming))
        val appName = extras.getString(EXTRA_APP_NAME, getString(R.string.call_default_app))

        val sessionId = extras.getLong(EXTRA_SESSION_ID, NotificationIdRegistry.NO_SESSION)

        Log.i(TAG, "onCreateIncomingConnection: caller='$callerName' uid=$uid app='$appName'")
        DiagnosticsLog.log("telecom: connection created ${sinceReport()} ms after report")

        val connection = AncsCallConnection(
            notificationUid = uid,
            onAnswer = { answerUid ->
                // Send ANCS positive action via AncsService, bound to the call's session
                val intent = Intent(this, AncsService::class.java).apply {
                    action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, answerUid)
                    putExtra(AncsService.EXTRA_ACTION_ID, app.tandemwear.watch.ble.AncsConstants.ACTION_POSITIVE)
                    putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, sessionId)
                }
                startService(intent)
                activeConnection = null
            },
            onReject = { rejectUid ->
                // Send ANCS negative action via AncsService, bound to the call's session
                val intent = Intent(this, AncsService::class.java).apply {
                    action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, rejectUid)
                    putExtra(AncsService.EXTRA_ACTION_ID, app.tandemwear.watch.ble.AncsConstants.ACTION_NEGATIVE)
                    putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, sessionId)
                }
                startService(intent)
                activeConnection = null
            }
        )

        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setAddress(
            Uri.fromParts("tel", "ANCS", null),
            TelecomManager.PRESENTATION_ALLOWED
        )
        connection.setRinging()

        activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        DiagnosticsLog.log("telecom: incoming connection refused ${sinceReport()} ms after report — notification + screen only")
        // If Telecom rejects our call, the CallStyle notification from
        // AncsService will still be posted as a fallback.
    }
}
