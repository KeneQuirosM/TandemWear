package app.tandemwear.watch.ancs

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import app.tandemwear.watch.data.PairedPhoneStore

/**
 * Restarts AncsService after device boot if an iPhone is paired.
 *
 * The connectedDevice foreground service type requires a granted Bluetooth runtime
 * permission; starting it without one throws, so check first.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val granted = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || PairedPhoneStore(context).pairedAddress == null) {
            Log.i(TAG, "Boot completed; not starting (paired=${granted && PairedPhoneStore(context).pairedAddress != null})")
            return
        }
        Log.i(TAG, "Boot completed, starting AncsService")
        val serviceIntent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_RECONNECT
        }
        context.startForegroundService(serviceIntent)
    }
}
