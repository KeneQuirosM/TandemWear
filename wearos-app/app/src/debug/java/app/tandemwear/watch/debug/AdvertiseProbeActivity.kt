package app.tandemwear.watch.debug

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.tandemwear.watch.ble.AncsConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Debug-only experiment (not shipped in release builds).
 *
 * Question it answers: can this watch act as a BLE peripheral that an iPhone lists in
 * Settings › Bluetooth, and once the iPhone connects and bonds, can the watch open a
 * GATT client over that link and see ANCS/AMS?
 *
 * Steps: advertise (connectable, ANCS service solicitation) → iPhone connects →
 * watch opens GATT client on the inbound link → discover services → report ANCS/AMS.
 * Everything is logged on screen and to logcat with tag "TWProbe".
 */
@SuppressLint("MissingPermission")
class AdvertiseProbeActivity : Activity() {

    companion object {
        private const val TAG = "TWProbe"
        private const val PERMISSION_REQUEST = 1
        private val AMS_SERVICE_UUID: UUID = UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC")
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var logView: TextView
    private lateinit var toggle: Button

    private val manager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val adapter: BluetoothAdapter? get() = manager.adapter

    private var advertisingSet: AdvertisingSet? = null
    private var gattServer: BluetoothGattServer? = null
    private var clientGatt: BluetoothGatt? = null
    private var isAdvertising = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pad = (resources.displayMetrics.density * 24).toInt()
        logView = TextView(this).apply { textSize = 11f }
        toggle = Button(this).apply {
            text = "Start"
            setOnClickListener { if (isAdvertising) stopProbe() else startProbe() }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad * 2)
            addView(TextView(this@AdvertiseProbeActivity).apply {
                text = "TandemWear BLE probe"
                textSize = 14f
                gravity = Gravity.CENTER
            })
            addView(toggle)
            addView(logView)
        }
        setContentView(ScrollView(this).apply { addView(column) })

        registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        reportCapabilities()
        requestPermissionsIfNeeded()
    }

    override fun onDestroy() {
        stopProbe()
        try {
            unregisterReceiver(bondReceiver)
        } catch (_: IllegalArgumentException) { }
        super.onDestroy()
    }

    private fun requiredPermissions() = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissions.forEachIndexed { i, p ->
            log("permission ${p.substringAfterLast('.')}: ${if (grantResults[i] == PackageManager.PERMISSION_GRANTED) "granted" else "DENIED"}")
        }
    }

    private fun reportCapabilities() {
        val a = adapter
        log("SDK ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}")
        if (a == null) {
            log("RESULT: no Bluetooth adapter")
            return
        }
        log("adapter enabled: ${a.isEnabled}")
        log("multiple advertisement supported: ${a.isMultipleAdvertisementSupported}")
        log("LE extended advertising supported: ${a.isLeExtendedAdvertisingSupported}")
        log("peripheral max adv data length: ${a.leMaximumAdvertisingDataLength}")
        if (!a.isMultipleAdvertisementSupported) {
            log("RESULT: this watch does NOT allow apps to advertise — plan B needed")
        }
    }

    private fun startProbe() {
        val a = adapter ?: return
        if (requiredPermissions().any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            log("missing permissions; grant them and retry")
            requestPermissionsIfNeeded()
            return
        }
        if (!a.isEnabled) {
            log("Bluetooth is off")
            return
        }

        // GATT server: needed to observe the iPhone's inbound connection on the peripheral side
        gattServer = manager.openGattServer(this, serverCallback)
        log("GATT server opened: ${gattServer != null}")

        val advertiser = a.bluetoothLeAdvertiser
        if (advertiser == null) {
            log("RESULT: bluetoothLeAdvertiser is null — advertising not available")
            return
        }

        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true) // iOS scans legacy advertising
            .setConnectable(true)
            .setScannable(true)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .build()

        // Legacy payload is 31 bytes: flags (3) + 128-bit solicitation (18) fit; the name goes in the scan response
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceSolicitationUuid(ParcelUuid(AncsConstants.ANCS_SERVICE_UUID))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        log("advertising as \"${a.name}\" with ANCS solicitation…")
        advertiser.startAdvertisingSet(parameters, data, scanResponse, null, null, advertisingCallback)
    }

    private fun stopProbe() {
        adapter?.bluetoothLeAdvertiser?.stopAdvertisingSet(advertisingCallback)
        advertisingSet = null
        clientGatt?.close()
        clientGatt = null
        gattServer?.close()
        gattServer = null
        if (isAdvertising) log("stopped")
        isAdvertising = false
        main.post { toggle.text = "Start" }
    }

    private val advertisingCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(set: AdvertisingSet?, txPower: Int, status: Int) {
            if (status == ADVERTISE_SUCCESS) {
                advertisingSet = set
                isAdvertising = true
                main.post { toggle.text = "Stop" }
                log("ADVERTISING OK (tx=$txPower dBm). On the iPhone open Settings › Bluetooth and look for \"${adapter?.name}\".")
            } else {
                log("RESULT: advertising FAILED, status=$status (${advertiseStatusName(status)})")
            }
        }

        override fun onAdvertisingSetStopped(set: AdvertisingSet?) {
            log("advertising set stopped")
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("INBOUND connection from ${device.address} (bond=${bondName(device.bondState)})")
                    openClient(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> log("inbound link closed by ${device.address}, status=$status")
            }
        }
    }

    /** Act as GATT client over the link the iPhone opened, to look for ANCS/AMS. */
    private fun openClient(device: BluetoothDevice) {
        if (clientGatt != null) return
        log("opening GATT client on ${device.address}…")
        clientGatt = device.connectGatt(this, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("GATT client connected, discovering services…")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("GATT client disconnected, status=$status")
                    gatt.close()
                    if (clientGatt === gatt) clientGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val uuids = gatt.services.map { it.uuid }
            log("services (${uuids.size}, status=$status): ${uuids.joinToString { it.toString().take(8) }}")
            val ancs = uuids.contains(AncsConstants.ANCS_SERVICE_UUID)
            val ams = uuids.contains(AMS_SERVICE_UUID)
            log("ANCS visible: $ancs · AMS visible: $ams · bond=${bondName(gatt.device.bondState)}")
            if (ancs) {
                log("RESULT: SUCCESS — iPhone connected to the watch and ANCS is reachable")
            } else if (gatt.device.bondState != BluetoothDevice.BOND_BONDED) {
                log("ANCS hidden until bonded; accept pairing on both devices")
            }
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            log("bond ${device.address}: ${bondName(state)}")
            if (state == BluetoothDevice.BOND_BONDED) {
                // ANCS only appears after bonding: rediscover on the existing client
                main.postDelayed({ clientGatt?.discoverServices() }, 1_000)
            }
        }
    }

    private fun bondName(state: Int) = when (state) {
        BluetoothDevice.BOND_BONDED -> "BONDED"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        else -> "NONE"
    }

    private fun advertiseStatusName(status: Int) = when (status) {
        AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
        AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
        AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
        AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
        AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
        else -> "unknown"
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        main.post { logView.append("$time $message\n") }
    }
}
