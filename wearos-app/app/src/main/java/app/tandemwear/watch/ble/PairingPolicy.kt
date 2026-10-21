package app.tandemwear.watch.ble

/**
 * Pure decision rules for pairing, inbound links and bond loss. No Android types, so
 * it is unit-tested directly; [BleConnectionManager] and the service apply the results.
 *
 * Addresses: iPhones use resolvable private addresses (RPA) that rotate. Before bonding
 * the watch only sees the RPA. Bonding distributes the iPhone's identity address and IRK,
 * after which Android resolves every new RPA to that identity address. So the paired
 * phone is identified by the identity address that appears in the bonded-device list
 * when bonding completes — never by an address seen before bonding.
 */
object PairingPolicy {

    // GATT status codes (BluetoothGatt) that mean the link isn't authenticated/encrypted.
    const val GATT_INSUFFICIENT_AUTHENTICATION = 0x05
    const val GATT_INSUFFICIENT_AUTHORIZATION = 0x08
    const val GATT_INSUFFICIENT_ENCRYPTION = 0x0F
    const val GATT_AUTH_FAIL = 0x89

    // HCI disconnect reasons that indicate the peer no longer has our keys.
    const val HCI_AUTHENTICATION_FAILURE = 0x05
    const val HCI_PIN_OR_KEY_MISSING = 0x06
    const val HCI_MIC_FAILURE = 0x3D

    /** A GATT operation failed because the link isn't paired/encrypted. */
    fun isAuthError(gattStatus: Int): Boolean = gattStatus == GATT_INSUFFICIENT_AUTHENTICATION ||
        gattStatus == GATT_INSUFFICIENT_AUTHORIZATION ||
        gattStatus == GATT_INSUFFICIENT_ENCRYPTION ||
        gattStatus == GATT_AUTH_FAIL

    /** A disconnect reason suggesting the iPhone forgot the watch (keys rejected). */
    fun isKeyLossDisconnect(status: Int): Boolean = status == HCI_AUTHENTICATION_FAILURE ||
        status == HCI_PIN_OR_KEY_MISSING ||
        status == HCI_MIC_FAILURE

    /**
     * The identity address created by a bonding attempt: the single address present in the
     * bonded list after bonding that wasn't there before. Null if zero or several are new.
     */
    fun newlyBondedAddress(before: Set<String>, after: Set<String>): String? {
        val beforeNormalized = before.map { it.uppercase() }.toSet()
        return after.map { it.uppercase() }.filter { it !in beforeNormalized }.singleOrNull()
    }

    enum class InboundDecision {
        /** Open the ANCS client on this link. */
        ATTACH,
        /** Bonded device that doesn't match the stored address: try quietly, drop if no ANCS. */
        PROBE,
        /** Not ours (e.g. another accessory connecting to the watch). */
        IGNORE
    }

    /**
     * What to do when a device opens an LE link to the watch (we're advertising).
     *
     * @param address address reported for the link (identity address once bonded)
     * @param isBonded whether Android reports the device as bonded
     * @param pairedAddress identity address of the paired iPhone, or null if none yet
     * @param pairingWindowOpen the user is pairing right now (onboarding)
     */
    fun onInboundLink(
        address: String,
        isBonded: Boolean,
        pairedAddress: String?,
        pairingWindowOpen: Boolean
    ): InboundDecision = when {
        pairedAddress != null && address.equals(pairedAddress, ignoreCase = true) -> InboundDecision.ATTACH
        pairingWindowOpen -> InboundDecision.ATTACH
        pairedAddress != null && isBonded -> InboundDecision.PROBE
        else -> InboundDecision.IGNORE
    }
}

/**
 * Counts consecutive signs that the iPhone lost the bond (it was "forgotten" in iOS
 * Settings). Below Android 16, or when the OEM doesn't send ACTION_KEY_MISSING, this is
 * the only signal: encrypted reconnects fail and the link drops with an auth reason.
 * A single failure can be a glitch, so it takes [threshold] in a row.
 */
class KeyLossDetector(private val threshold: Int = DEFAULT_THRESHOLD) {

    companion object {
        const val DEFAULT_THRESHOLD = 2
    }

    private var consecutive = 0

    /** Record one signal; returns true once the threshold is reached. */
    fun onSignal(): Boolean {
        consecutive++
        return consecutive >= threshold
    }

    /** An ANCS session came up: keys are fine. */
    fun onSessionEstablished() {
        consecutive = 0
    }

    val count: Int get() = consecutive
}
