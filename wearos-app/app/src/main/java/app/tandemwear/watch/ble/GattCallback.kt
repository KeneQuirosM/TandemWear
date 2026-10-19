package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.util.Log

/**
 * BluetoothGattCallback implementation that routes ANCS characteristic changes
 * to the appropriate handlers.
 *
 * Callbacks arrive on a binder thread. Handlers must not touch shared state directly;
 * [BleConnectionManager] hops them onto its single-threaded dispatcher.
 * Uses the API 33 callback signatures (minSdk 33), where values are passed by
 * parameter instead of through the mutable `characteristic.value`.
 */
@SuppressLint("MissingPermission")
class GattCallback(
    private val onConnected: (BluetoothGatt) -> Unit,
    private val onDisconnected: (BluetoothGatt, Int) -> Unit,
    private val onServicesDiscoveredCallback: (BluetoothGatt, Int) -> Unit,
    private val onNotificationSourceChanged: (ByteArray) -> Unit,
    private val onDataSourceChanged: (ByteArray) -> Unit,
    private val onDescriptorWritten: (BluetoothGattDescriptor, Int) -> Unit,
    private val onCharacteristicWritten: (BluetoothGattCharacteristic, Int) -> Unit,
    private val onMtuChanged: (Int, Int) -> Unit,
    private val onCharacteristicReadCallback: (BluetoothGattCharacteristic, ByteArray, Int) -> Unit
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "GattCallback"
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(TAG, "Connection state changed: status=$status newState=$newState")
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i(TAG, "Connected to ${gatt.device.address}")
                onConnected(gatt)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i(TAG, "Disconnected from ${gatt.device.address} status=$status")
                onDisconnected(gatt, status)
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(TAG, "Services discovered: status=$status count=${gatt.services.size}")
        onServicesDiscoveredCallback(gatt, status)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        when (characteristic.uuid) {
            AncsConstants.NOTIFICATION_SOURCE_UUID -> onNotificationSourceChanged(value)
            AncsConstants.DATA_SOURCE_UUID -> onDataSourceChanged(value)
            else -> Log.d(TAG, "Unhandled characteristic changed: ${characteristic.uuid}")
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        Log.d(TAG, "Descriptor written: ${descriptor.characteristic.uuid} status=$status")
        onDescriptorWritten(descriptor, status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        onCharacteristicWritten(characteristic, status)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        onCharacteristicReadCallback(characteristic, value, status)
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.d(TAG, "MTU changed: mtu=$mtu status=$status")
        onMtuChanged(mtu, status)
    }
}
