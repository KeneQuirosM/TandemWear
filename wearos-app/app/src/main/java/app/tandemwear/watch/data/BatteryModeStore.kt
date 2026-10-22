package app.tandemwear.watch.data

import android.content.Context
import app.tandemwear.watch.battery.BatteryPolicy

/** Debug switch (TW Probe ANCS): follow the iPhone's battery by notifications or by polling. */
class BatteryModeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var mode: BatteryPolicy.Mode
        get() = prefs.getString(KEY_MODE, null)
            ?.let { name -> BatteryPolicy.Mode.entries.firstOrNull { it.name == name } }
            ?: BatteryPolicy.Mode.SUBSCRIBE
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    private companion object {
        const val PREFS = "battery_debug"
        const val KEY_MODE = "mode"
    }
}
