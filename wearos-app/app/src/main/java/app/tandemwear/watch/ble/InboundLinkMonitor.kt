package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import app.tandemwear.watch.diag.DiagnosticsLog

/**
 * Observes links the iPhone opens to the watch. While the watch advertises, the iPhone is
 * the one that connects (it is the GAP central); a GATT server with no services is the
 * public way to be told about those inbound links. ANCS itself is then used through a
 * GATT *client* on the same link (see [BleConnectionManager.attachInbound]).
 *
 * Callbacks arrive on a binder thread.
 */
@SuppressLint("MissingPermission")
class InboundLinkMonitor(
    private val context: Context,
    private val onLinkUp: (BluetoothDevice) -> Unit,
    private val onLinkDown: (BluetoothDevice, Int) -> Unit
) {

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var server: BluetoothGattServer? = null

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    DiagnosticsLog.log("inbound: link up ${describe(device)}")
                    onLinkUp(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    DiagnosticsLog.log("inbound: link down ${describe(device)} status=$status")
                    onLinkDown(device, status)
                }
            }
        }
    }

    fun open(): Boolean {
        if (server != null) return true
        server = manager.openGattServer(context, callback)
        if (server == null) DiagnosticsLog.log("inbound: could not open GATT server")
        return server != null
    }

    fun close() {
        server?.close()
        server = null
    }

    companion object {
        /** Address, transport type and bond state — enough to tell links apart in logs. */
        fun describe(device: BluetoothDevice): String {
            val type = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
                else -> "UNKNOWN"
            }
            val bond = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "BONDED"
                BluetoothDevice.BOND_BONDING -> "BONDING"
                else -> "NONE"
            }
            return "${device.address} type=$type bond=$bond"
        }
    }
}
