package app.tandemwear.watch.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * Remembers which bonded device is the user's iPhone.
 *
 * What is stored is the *identity* address Android lists in its bonded devices after
 * bonding (see [app.tandemwear.watch.ble.PairingPolicy.newlyBondedAddress]) — not the
 * rotating private address seen before pairing. It is only trusted while the bond exists:
 * [bondedPhone] returns null as soon as the bond is gone, and the app goes back to pairing.
 */
@SuppressLint("MissingPermission")
class PairedPhoneStore(context: Context) {

    companion object {
        private const val PREFS = "paired_phone"
        private const val KEY_ADDRESS = "identity_address"
        private const val KEY_NAME = "name"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    val pairedAddress: String? get() = prefs.getString(KEY_ADDRESS, null)

    val pairedName: String? get() = prefs.getString(KEY_NAME, null)

    fun save(identityAddress: String, name: String?) {
        prefs.edit()
            .putString(KEY_ADDRESS, identityAddress.uppercase())
            .putString(KEY_NAME, name)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Addresses currently bonded to the watch (needs BLUETOOTH_CONNECT). */
    fun bondedAddresses(): Set<String> = try {
        manager.adapter?.bondedDevices?.map { it.address.uppercase() }?.toSet().orEmpty()
    } catch (e: SecurityException) {
        emptySet()
    }

    /** The paired iPhone if it is still bonded, else null. */
    fun bondedPhone(): BluetoothDevice? {
        val address = pairedAddress ?: return null
        return try {
            manager.adapter?.bondedDevices?.firstOrNull { it.address.equals(address, ignoreCase = true) }
        } catch (e: SecurityException) {
            null
        }
    }
}
