package app.tandemwear.watch

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import app.tandemwear.watch.diag.DiagnosticsLog
import app.tandemwear.watch.ble.AncsConstants

class AncsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Notification text may appear in diagnostics only in debuggable builds
        DiagnosticsLog.allowContent = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Delete ALL old channel versions so settings take effect.
        // Android caches channel settings permanently once created.
        val oldVersions = listOf("", "_v2", "_v3", "_v4", "_v5", "_v6")
        val channelNames = listOf(
            "ancs_service", "ancs_incoming_call", "ancs_messages",
            "ancs_email", "ancs_social", "ancs_schedule", "ancs_other"
        )
        for (v in oldVersions) {
            for (name in channelNames) {
                manager.deleteNotificationChannel("$name$v")
            }
        }

        // Channels use system defaults for vibration and sound — this ensures
        // the app respects the user's watch-level vibration/sound settings and
        // gets proper heads-up/overlay display. No manual overrides.
        val channels = listOf(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_service_desc)
            },

            NotificationChannel(
                CHANNEL_INCOMING_CALL,
                getString(R.string.channel_calls),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_calls_desc)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },

            NotificationChannel(
                CHANNEL_MESSAGES,
                getString(R.string.channel_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_messages_desc)
            },

            NotificationChannel(
                CHANNEL_EMAIL,
                getString(R.string.channel_email),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_email_desc)
            },

            NotificationChannel(
                CHANNEL_SOCIAL,
                getString(R.string.channel_social),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_social_desc)
            },

            NotificationChannel(
                CHANNEL_SCHEDULE,
                getString(R.string.channel_schedule),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_schedule_desc)
                setBypassDnd(true)
            },

            NotificationChannel(
                CHANNEL_OTHER,
                getString(R.string.channel_other),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_other_desc)
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        // Channel IDs are versioned — bump the suffix when changing channel settings
        // because Android caches channel settings permanently once created.
        private const val CHANNEL_VERSION = "v7"
        const val CHANNEL_SERVICE = "ancs_service_$CHANNEL_VERSION"
        const val CHANNEL_INCOMING_CALL = "ancs_incoming_call_$CHANNEL_VERSION"
        const val CHANNEL_MESSAGES = "ancs_messages_$CHANNEL_VERSION"
        const val CHANNEL_EMAIL = "ancs_email_$CHANNEL_VERSION"
        const val CHANNEL_SOCIAL = "ancs_social_$CHANNEL_VERSION"
        const val CHANNEL_SCHEDULE = "ancs_schedule_$CHANNEL_VERSION"
        const val CHANNEL_OTHER = "ancs_other_$CHANNEL_VERSION"

        fun channelForCategory(categoryId: Int): String = when (categoryId) {
            AncsConstants.CATEGORY_INCOMING_CALL -> CHANNEL_INCOMING_CALL
            AncsConstants.CATEGORY_MISSED_CALL -> CHANNEL_MESSAGES
            AncsConstants.CATEGORY_VOICEMAIL -> CHANNEL_MESSAGES
            AncsConstants.CATEGORY_SOCIAL -> CHANNEL_SOCIAL
            AncsConstants.CATEGORY_SCHEDULE -> CHANNEL_SCHEDULE
            AncsConstants.CATEGORY_EMAIL -> CHANNEL_EMAIL
            AncsConstants.CATEGORY_NEWS -> CHANNEL_OTHER
            AncsConstants.CATEGORY_HEALTH_FITNESS -> CHANNEL_OTHER
            AncsConstants.CATEGORY_BUSINESS_FINANCE -> CHANNEL_OTHER
            AncsConstants.CATEGORY_LOCATION -> CHANNEL_OTHER
            AncsConstants.CATEGORY_ENTERTAINMENT -> CHANNEL_OTHER
            else -> CHANNEL_OTHER
        }
    }
}
