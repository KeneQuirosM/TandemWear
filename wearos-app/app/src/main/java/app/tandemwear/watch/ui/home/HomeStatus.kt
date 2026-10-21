package app.tandemwear.watch.ui.home

import app.tandemwear.watch.model.ConnectionState

/**
 * What the home screen shows, derived from the service's [ConnectionState] plus facts the
 * BLE layer doesn't model (adapter on/off, whether an iPhone is paired). Pure, so the
 * mapping is unit-tested; the UI only renders it.
 */
sealed class HomeStatus {
    /** Each status has exactly one main action. */
    enum class Action { NONE, OPEN_BLUETOOTH_SETTINGS, RECONNECT, PAIR, REPAIR }

    abstract val action: Action

    data class Connected(val phoneName: String?) : HomeStatus() {
        override val action = Action.NONE
    }

    /**
     * Linked to the paired iPhone, but it isn't sharing notifications (no ANCS). Usually
     * clears by itself; the BLE layer keeps handling it as a reconnection.
     */
    data class LinkedNoNotifications(val phoneName: String?) : HomeStatus() {
        override val action = Action.NONE
    }

    /** Link being (re)established with the paired iPhone. */
    data class Reconnecting(val phoneName: String?) : HomeStatus() {
        override val action = Action.NONE
    }

    /** Paired, but no link right now (out of range, iPhone Bluetooth off, error). */
    data class Disconnected(val errorDetail: String?) : HomeStatus() {
        override val action = Action.RECONNECT
    }

    /** The iPhone forgot the watch: pair again. */
    data object RepairNeeded : HomeStatus() {
        override val action = Action.REPAIR
    }

    /** The watch's own Bluetooth is off. Takes priority over everything else. */
    data object BluetoothOff : HomeStatus() {
        override val action = Action.OPEN_BLUETOOTH_SETTINGS
    }

    /** No iPhone paired yet (or pairing in progress from the pairing screen). */
    data object NotPaired : HomeStatus() {
        override val action = Action.PAIR
    }

    companion object {
        fun from(state: ConnectionState, bluetoothOn: Boolean, hasPairedPhone: Boolean): HomeStatus {
            if (!bluetoothOn) return BluetoothOff
            return when (state) {
                is ConnectionState.Connected -> Connected(state.deviceName)
                is ConnectionState.RepairNeeded -> RepairNeeded
                is ConnectionState.Connecting -> if (hasPairedPhone) Reconnecting(state.deviceName) else NotPaired
                is ConnectionState.Bonding -> if (hasPairedPhone) Reconnecting(state.deviceName) else NotPaired
                is ConnectionState.LinkedWithoutAncs ->
                    if (hasPairedPhone) LinkedNoNotifications(state.deviceName) else NotPaired
                is ConnectionState.Error -> if (hasPairedPhone) Disconnected(state.message) else NotPaired
                is ConnectionState.Disconnected,
                is ConnectionState.Idle,
                is ConnectionState.WaitingForIphone -> if (hasPairedPhone) Disconnected(null) else NotPaired
            }
        }
    }
}
