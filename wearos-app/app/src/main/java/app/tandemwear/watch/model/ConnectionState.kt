package app.tandemwear.watch.model

sealed class ConnectionState {
    data object Idle : ConnectionState()
    /** Advertising for first pairing; the user must tap the watch in iPhone › Settings › Bluetooth. */
    data class WaitingForIphone(val advertisedName: String?) : ConnectionState()
    data class Connecting(val deviceName: String?) : ConnectionState()
    data class Bonding(val deviceName: String?) : ConnectionState()
    /** ANCS session active. [sessionId] changes on every new session (UIDs are per session). */
    data class Connected(val deviceName: String?, val sessionId: Long = 0L) : ConnectionState()
    data class Disconnected(val reason: String? = null) : ConnectionState()
    /** The iPhone no longer accepts our keys (it "forgot" the watch): pair again. */
    data class RepairNeeded(val reason: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
