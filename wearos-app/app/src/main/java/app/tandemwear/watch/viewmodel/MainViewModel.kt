package app.tandemwear.watch.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import app.tandemwear.watch.ancs.AncsService
import app.tandemwear.watch.data.PairedPhoneStore
import app.tandemwear.watch.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

@SuppressLint("MissingPermission")
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val pairedPhoneStore = PairedPhoneStore(application)
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    // Observe the service's shared connection state directly
    val connectionState: StateFlow<ConnectionState> = AncsService.sharedConnectionState

    val isBluetoothEnabled: Boolean get() = bluetoothManager.adapter?.isEnabled == true

    /** Name the iPhone will list in Settings › Bluetooth. */
    val watchName: String
        get() = try {
            bluetoothManager.adapter?.name
        } catch (e: SecurityException) {
            null
        } ?: "Galaxy Watch"

    /** Make the watch visible to the iPhone for pairing (the user taps it in iPhone Settings). */
    fun startPairing() = sendToService(AncsService.ACTION_START_PAIRING, foreground = true)

    fun startService() = sendToService(AncsService.ACTION_START, foreground = true)

    fun stopService() = sendToService(AncsService.ACTION_STOP, foreground = false)

    fun disconnect() = sendToService(AncsService.ACTION_DISCONNECT, foreground = false)

    /** True while an iPhone is paired and its bond still exists on the watch. */
    fun hasPairedIPhone(): Boolean = pairedPhoneStore.bondedPhone() != null

    /**
     * Clear all TandemWear notifications from the watch.
     * Frees up slots so new notifications can come through.
     */
    fun clearAllNotifications() {
        val context = getApplication<Application>()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Cancel all except the foreground service notification (ID 1)
        nm.activeNotifications.forEach { sbn ->
            if (sbn.id != 1) { // service notification
                nm.cancel(sbn.id)
            }
        }
        // Also tell the service to reset its tracking
        sendToService(AncsService.ACTION_CLEAR_ALL, foreground = false)
    }

    private fun sendToService(action: String, foreground: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, AncsService::class.java).setAction(action)
        if (foreground) context.startForegroundService(intent) else context.startService(intent)
    }
}
