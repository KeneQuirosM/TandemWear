package app.tandemwear.watch.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tandemwear.watch.ancs.AncsService
import app.tandemwear.watch.ancs.NotificationActionReceiver
import app.tandemwear.watch.data.history.HistoryEntry
import app.tandemwear.watch.data.history.HistoryRepository
import app.tandemwear.watch.data.history.TandemDatabase
import app.tandemwear.watch.data.PairedPhoneStore
import app.tandemwear.watch.model.ConnectionState
import app.tandemwear.watch.ui.home.HomeStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val pairedPhoneStore = PairedPhoneStore(application)
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val history = HistoryRepository(TandemDatabase.get(application).historyDao())

    // Observe the service's shared connection state directly
    val connectionState: StateFlow<ConnectionState> = AncsService.sharedConnectionState

    val isBluetoothEnabled: Boolean get() = bluetoothManager.adapter?.isEnabled == true

    /** Watch's own Bluetooth adapter on/off, from the system broadcast (no BLE-layer change). */
    private val bluetoothOn: StateFlow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                trySend(state == BluetoothAdapter.STATE_ON)
            }
        }
        application.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        trySend(isBluetoothEnabled)
        awaitClose { application.unregisterReceiver(receiver) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), isBluetoothEnabled)

    /** What the home screen shows. hasPairedIPhone is re-read on every state change. */
    val homeStatus: StateFlow<HomeStatus> = combine(connectionState, bluetoothOn) { state, btOn ->
        HomeStatus.from(state, bluetoothOn = btOn, hasPairedPhone = hasPairedIPhone())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeStatus.from(connectionState.value, isBluetoothEnabled, hasPairedIPhone())
    )

    /** Last [HistoryRepository.SHOW] notifications, newest first. */
    val recent: StateFlow<List<HistoryEntry>> = history.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Session whose UIDs are valid on the iPhone right now (null when not connected). */
    val currentSessionId: StateFlow<Long?> = connectionState
        .map { (it as? ConnectionState.Connected)?.sessionId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun historyEntry(id: Long): Flow<HistoryEntry?> = history.entry(id)

    /** Sends the iPhone action through the same path as the notification buttons. */
    fun performAction(entry: HistoryEntry, positive: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, entry.uid)
            putExtra(AncsService.EXTRA_ACTION_ID, if (positive) 0 else 1)
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, entry.sessionId)
            putExtra(NotificationActionReceiver.EXTRA_ANDROID_ID, entry.androidId)
        }
        context.startService(intent)
        dismiss(entry)
    }

    /** Removes it from the watch only (history and notification shade). */
    fun dismiss(entry: HistoryEntry) {
        val nm = getApplication<Application>().getSystemService(NotificationManager::class.java)
        nm.cancel(entry.androidId)
        viewModelScope.launch(Dispatchers.IO) { history.dismiss(entry.id) }
    }

    /** Name the iPhone will list in Settings › Bluetooth. */
    val watchName: String
        get() = try {
            bluetoothManager.adapter?.name
        } catch (e: SecurityException) {
            null
        } ?: "Galaxy Watch"

    /** versionName from the installed package (BuildConfig is not generated). */
    val appVersion: String
        get() = try {
            val app = getApplication<Application>()
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        } catch (e: Exception) {
            null
        } ?: "?"

    /** Make the watch visible to the iPhone for pairing (the user taps it in iPhone Settings). */
    fun startPairing() = sendToService(AncsService.ACTION_START_PAIRING, foreground = true)

    fun startService() = sendToService(AncsService.ACTION_START, foreground = true)

    fun stopService() = sendToService(AncsService.ACTION_STOP, foreground = false)

    fun disconnect() = sendToService(AncsService.ACTION_DISCONNECT, foreground = false)

    /** True while an iPhone is paired and its bond still exists on the watch. */
    fun hasPairedIPhone(): Boolean = pairedPhoneStore.bondedPhone() != null

    /** Opens the watch's Bluetooth settings; false if the system has no such screen. */
    fun openBluetoothSettings(): Boolean {
        val context = getApplication<Application>()
        return try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

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
