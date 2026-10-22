package app.tandemwear.watch.ancs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives notification action button presses (positive/negative)
 * and forwards them to AncsService for Control Point writing.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifActionReceiver"
        const val ACTION_POSITIVE = "app.tandemwear.watch.ACTION_POSITIVE"
        const val ACTION_NEGATIVE = "app.tandemwear.watch.ACTION_NEGATIVE"
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
        /** ANCS session the UID belongs to (UIDs are renumbered on every reconnect). */
        const val EXTRA_SESSION_ID = "ancs_session_id"
        /** Android notification id, so a stale notification can still be dismissed locally. */
        const val EXTRA_ANDROID_ID = "android_notification_id"

        /**
         * Explicit intent for this receiver. It has no intent-filter, so an intent that only
         * names the package never reaches it (that is why notification buttons did nothing).
         */
        fun intent(context: Context, action: String, uid: Long): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_NOTIFICATION_UID, uid)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getLongExtra(EXTRA_NOTIFICATION_UID, -1)
        if (uid == -1L) {
            Log.w(TAG, "No notification UID in intent")
            return
        }

        val actionId = when (intent.action) {
            ACTION_POSITIVE -> 0
            ACTION_NEGATIVE -> 1
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                return
            }
        }

        Log.d(TAG, "Notification action: uid=$uid actionId=$actionId")

        // Forward to service
        val serviceIntent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
            putExtra(EXTRA_NOTIFICATION_UID, uid)
            putExtra(AncsService.EXTRA_ACTION_ID, actionId)
            putExtra(EXTRA_SESSION_ID, intent.getLongExtra(EXTRA_SESSION_ID, 0L))
            putExtra(EXTRA_ANDROID_ID, intent.getIntExtra(EXTRA_ANDROID_ID, -1))
        }
        context.startService(serviceIntent)
    }
}
