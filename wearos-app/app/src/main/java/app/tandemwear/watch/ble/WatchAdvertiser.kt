package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.ParcelUuid
import app.tandemwear.watch.diag.DiagnosticsLog

/**
 * Advertises the watch as a connectable BLE peripheral that solicits ANCS, so the iPhone
 * lists it in Settings › Bluetooth (first pairing) and reconnects to it afterwards.
 * Verified on Galaxy Watch 4 / SDK 36 with the TW Probe experiment.
 *
 * Legacy advertising only (iOS scans legacy PDUs; the Watch 4 has no extended advertising):
 * flags + 128-bit ANCS solicitation = 21 of 31 bytes; the device name goes in the scan
 * response.
 */
@SuppressLint("MissingPermission")
class WatchAdvertiser(context: Context) {

    enum class Mode(val interval: Int) {
        /** Onboarding: ~100 ms interval so the iPhone finds the watch quickly. */
        PAIRING(AdvertisingSetParameters.INTERVAL_LOW),
        /** Paired but disconnected: ~1 s interval, low duty cycle for battery. */
        RECONNECT(AdvertisingSetParameters.INTERVAL_HIGH)
    }

    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @Volatile
    var mode: Mode? = null
        private set

    private var callback: AdvertisingSetCallback? = null

    val isAdvertising: Boolean get() = mode != null

    /** Start (or switch to) [newMode]. Returns false if advertising isn't available. */
    fun start(newMode: Mode): Boolean {
        if (mode == newMode) return true
        stop()
        val advertiser = adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser ?: run {
            DiagnosticsLog.log("advertise: unavailable (Bluetooth off?)")
            return false
        }
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(true)
            .setScannable(true)
            .setInterval(newMode.interval)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceSolicitationUuid(ParcelUuid(AncsConstants.ANCS_SERVICE_UUID))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        val cb = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(set: AdvertisingSet?, txPower: Int, status: Int) {
                if (status == ADVERTISE_SUCCESS) {
                    DiagnosticsLog.log("advertise: ${newMode.name} as \"${adapter.name}\"")
                } else {
                    DiagnosticsLog.log("advertise: FAILED status=$status")
                    if (callback === this) {
                        mode = null
                        callback = null
                    }
                }
            }
        }
        callback = cb
        mode = newMode
        advertiser.startAdvertisingSet(parameters, data, scanResponse, null, null, cb)
        return true
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        mode = null
        try {
            adapter?.bluetoothLeAdvertiser?.stopAdvertisingSet(cb)
            DiagnosticsLog.log("advertise: stopped")
        } catch (e: IllegalStateException) {
            // Adapter already off
        }
    }

    /** Name the iPhone shows in Settings › Bluetooth. */
    val advertisedName: String? get() = adapter?.name
}
