package app.tandemwear.watch.ancs

import android.annotation.SuppressLint
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.util.Log
import app.tandemwear.watch.diag.DiagnosticsLog

/**
 * Represents a single incoming call from ANCS within the Telecom framework.
 *
 * When the user answers or declines via the system call UI (which can show
 * over charging screen, lock screen, etc.), the corresponding ANCS action
 * is sent back to the iPhone via BLE.
 */
@SuppressLint("MissingPermission")
class AncsCallConnection(
    private val notificationUid: Long,
    private val onAnswer: (Long) -> Unit,
    private val onReject: (Long) -> Unit
) : Connection() {

    companion object {
        private const val TAG = "AncsCallConnection"
    }

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        // No HOLD: the call's audio is on the iPhone, nothing to hold on the watch
        connectionCapabilities = 0
    }

    override fun onShowIncomingCallUi() {
        // Telecom says we may show our ringing UI: the CallStyle notification already does
        DiagnosticsLog.log("telecom: onShowIncomingCallUi uid=$notificationUid")
    }

    override fun onAnswer() {
        Log.i(TAG, "Call answered via system UI, uid=$notificationUid")
        DiagnosticsLog.log("telecom: answered from the system UI uid=$notificationUid")
        onAnswer(notificationUid)
        // The call goes on on the iPhone: end ours properly instead of leaving it active
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onReject() {
        Log.i(TAG, "Call rejected via system UI, uid=$notificationUid")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        onReject(notificationUid)
        destroy()
    }

    override fun onDisconnect() {
        Log.i(TAG, "Call disconnected, uid=$notificationUid")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    /**
     * Update the caller display name after ANCS attributes arrive.
     */
    fun updateCallerName(name: String) {
        setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
    }

    /**
     * End the call externally (e.g., when ANCS sends REMOVE event).
     */
    fun endCall() {
        setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
        destroy()
    }
}
