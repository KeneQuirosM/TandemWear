package app.tandemwear.watch.data

import android.content.Context

/**
 * Debug knobs for incoming calls (TW Probe ANCS). [useTelecom] lets us measure what the
 * watch's Telecom (self-managed ConnectionService) adds before deciding to remove it.
 */
class CallDebugStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var useTelecom: Boolean
        get() = prefs.getBoolean(KEY_USE_TELECOM, true)
        set(value) {
            prefs.edit().putBoolean(KEY_USE_TELECOM, value).apply()
        }

    private companion object {
        const val PREFS = "call_debug"
        const val KEY_USE_TELECOM = "use_telecom"
    }
}
