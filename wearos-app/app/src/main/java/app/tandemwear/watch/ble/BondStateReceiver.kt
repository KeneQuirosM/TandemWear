package app.tandemwear.watch.ble

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Receives bond state changes. After bonding completes, ANCS service becomes accessible.
 */
class BondStateReceiver(
    private val onBonded: (BluetoothDevice) -> Unit,
    private val onBondFailed: (BluetoothDevice) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "BondStateReceiver"

        val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)

        /**
         * Android 16 (API 36) BluetoothDevice.ACTION_KEY_MISSING: a bonded device could not be
         * authenticated on reconnection; the system kept the bond and disconnected. Literal
         * because we compile against SDK 35. Delivery depends on the OEM.
         */
        const val ACTION_KEY_MISSING = "android.bluetooth.device.action.KEY_MISSING"

        /** Android 16 (API 36) BluetoothDevice.ACTION_ENCRYPTION_CHANGE and its boolean extra. */
        const val ACTION_ENCRYPTION_CHANGE = "android.bluetooth.device.action.ENCRYPTION_CHANGE"
        const val EXTRA_ENCRYPTION_ENABLED = "android.bluetooth.device.extra.ENCRYPTION_ENABLED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)

        Log.d(TAG, "Bond state changed: ${bondStateName(prevState)} -> ${bondStateName(bondState)} for ${device.address}")

        when (bondState) {
            BluetoothDevice.BOND_BONDED -> {
                Log.i(TAG, "Device bonded: ${device.address}")
                onBonded(device)
            }
            BluetoothDevice.BOND_NONE -> {
                if (prevState == BluetoothDevice.BOND_BONDING) {
                    Log.w(TAG, "Bond failed for: ${device.address}")
                    onBondFailed(device)
                }
            }
        }
    }

    private fun bondStateName(state: Int): String = when (state) {
        BluetoothDevice.BOND_NONE -> "NONE"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        BluetoothDevice.BOND_BONDED -> "BONDED"
        else -> "UNKNOWN($state)"
    }
}
