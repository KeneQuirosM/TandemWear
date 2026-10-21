package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.util.Log
import app.tandemwear.watch.ble.GattOperationQueue.OpKey
import app.tandemwear.watch.diag.DiagnosticsLog
import app.tandemwear.watch.model.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Manages the GATT client connection to the iPhone and the ANCS subscription lifecycle.
 *
 * Links come up two ways:
 * - the iPhone connects to the watch while it advertises ([attachInbound]), used for the
 *   first pairing and for reconnection;
 * - a background `autoConnect` client to the bonded identity address ([reconnect]).
 *
 * Flow: link → MTU → discover → bond (required: ANCS characteristics need authorization)
 *       → subscribe DS → subscribe NS → active
 *
 * Threading: all mutable state lives on [bleDispatcher] (one thread at a time). GATT
 * callbacks arrive on binder threads and are hopped onto it; every GATT operation goes
 * through [GattOperationQueue] so only one is in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("MissingPermission")
class BleConnectionManager(
    private val context: Context,
    /** Current bonded addresses, to find which identity address a new bond created. */
    private val bondedAddresses: () -> Set<String>
) {

    companion object {
        private const val TAG = "BleConnectionManager"
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 3_000L
        private const val TARGET_MTU = 512
        private const val MAX_SERVICE_DISCOVERY_RETRIES = 2
        private const val EVENT_BUFFER_CAPACITY = 256
        /** The user has to accept the pairing prompt on the iPhone. */
        private const val BOND_TIMEOUT_MS = 90_000L
        /** A pending autoConnect client normally picks up an inbound link by itself. */
        private const val INBOUND_PICKUP_GRACE_MS = 2_000L

        private val GAP_SERVICE_UUID: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val GAP_DEVICE_NAME_UUID: UUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")
    }

    private val bleDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + bleDispatcher)
    private val operations = GattOperationQueue(scope)
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _notificationSourceEvents = MutableSharedFlow<ByteArray>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val notificationSourceEvents: SharedFlow<ByteArray> = _notificationSourceEvents.asSharedFlow()

    private val _dataSourceEvents = MutableSharedFlow<ByteArray>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val dataSourceEvents: SharedFlow<ByteArray> = _dataSourceEvents.asSharedFlow()

    /** Called (on the BLE thread) when bonding produced the iPhone's identity address. */
    @Volatile
    var onPhoneBonded: ((identityAddress: String, name: String?) -> Unit)? = null

    private var gatt: BluetoothGatt? = null
    private var linkUp = false
    /** How the current GATT client was opened — logged to tell reconnection paths apart. */
    private var openedWithAutoConnect = false

    /**
     * Whether the watch keeps a background autoConnect request to the bonded iPhone.
     * Debug builds can turn it off to test the "iPhone reconnects to the advertising
     * watch" path on its own.
     */
    @Volatile
    var backgroundReconnectEnabled = true
    private var targetDevice: BluetoothDevice? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var autoReconnect = true

    private var serviceDiscoveryRetries = 0
    private var notificationSourceChar: BluetoothGattCharacteristic? = null
    private var controlPointChar: BluetoothGattCharacteristic? = null
    private var dataSourceChar: BluetoothGattCharacteristic? = null
    private var isSubscribing = false
    private var lastDescriptorStatus = BluetoothGatt.GATT_SUCCESS

    /** Bonded addresses when the current link came up — diffed after bonding. */
    private var bondedSnapshot: Set<String> = emptySet()
    private var pendingBond: CompletableDeferred<Boolean>? = null
    private val keyLoss = KeyLossDetector()

    /** Cached real device name read from GAP characteristic */
    private var cachedDeviceName: String? = null

    val bondStateReceiver = BondStateReceiver(
        onBonded = { device ->
            scope.launch {
                DiagnosticsLog.log("bond: BONDED ${device.address}")
                val pending = pendingBond
                if (pending != null) {
                    pending.complete(true)
                } else if (gatt?.device?.address.equals(device.address, ignoreCase = true)) {
                    // Bond initiated by the stack/iPhone (e.g. after an auth error) — still record it
                    recordNewBond(device)
                }
            }
        },
        onBondFailed = { device ->
            scope.launch {
                DiagnosticsLog.log("bond: FAILED/REJECTED ${device.address}")
                pendingBond?.complete(false)
            }
        }
    )

    private val gattCallback = GattCallback(
        onConnected = { g -> scope.launch { handleConnected(g) } },
        onDisconnected = { g, status -> scope.launch { handleDisconnected(g, status) } },
        onServicesDiscoveredCallback = { g, status ->
            scope.launch {
                operations.onComplete(OpKey.DiscoverServices, status == BluetoothGatt.GATT_SUCCESS)
                handleServicesDiscovered(g, status)
            }
        },
        // NS/DS bytes are emitted straight from the binder thread to keep ordering and latency;
        // SharedFlow is thread-safe and the value arrays are fresh copies on API 33+.
        onNotificationSourceChanged = { data ->
            if (!_notificationSourceEvents.tryEmit(data)) Log.e(TAG, "NS event dropped: buffer full")
        },
        onDataSourceChanged = { data ->
            if (!_dataSourceEvents.tryEmit(data)) Log.e(TAG, "DS fragment dropped: buffer full")
        },
        onDescriptorWritten = { descriptor, status ->
            scope.launch {
                lastDescriptorStatus = status
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Descriptor write failed: $status for ${descriptor.characteristic.uuid}")
                }
                operations.onComplete(
                    OpKey.WriteDescriptor(descriptor.characteristic.uuid),
                    status == BluetoothGatt.GATT_SUCCESS
                )
            }
        },
        onCharacteristicWritten = { characteristic, status ->
            scope.launch {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Characteristic write failed: $status for ${characteristic.uuid}")
                }
                operations.onComplete(
                    OpKey.WriteCharacteristic(characteristic.uuid),
                    status == BluetoothGatt.GATT_SUCCESS
                )
            }
        },
        onMtuChanged = { mtu, status ->
            scope.launch {
                DiagnosticsLog.log("gatt: MTU $mtu (status=$status)")
                operations.onComplete(OpKey.Mtu, status == BluetoothGatt.GATT_SUCCESS)
            }
        },
        onCharacteristicReadCallback = { characteristic, value, status ->
            scope.launch {
                val ok = status == BluetoothGatt.GATT_SUCCESS
                operations.onComplete(OpKey.ReadCharacteristic(characteristic.uuid), ok)
                if (ok) onCharacteristicRead(characteristic.uuid, value)
            }
        }
    )

    /**
     * The iPhone opened a link to the watch (we were advertising). Open the GATT client
     * over that link. If a background autoConnect client to the same device is pending,
     * give it a moment to pick the link up by itself first.
     */
    fun attachInbound(device: BluetoothDevice) {
        scope.launch {
            val g = gatt
            val sameDevice = g?.device?.address.equals(device.address, ignoreCase = true)
            if (sameDevice && linkUp) return@launch
            if (sameDevice) {
                delay(INBOUND_PICKUP_GRACE_MS)
                if (gatt !== g || linkUp) return@launch
                DiagnosticsLog.log("gatt: background client didn't pick up the link, attaching directly")
            }
            openGatt(device, autoConnect = false)
        }
    }

    /**
     * Keep a background connection request to the bonded iPhone ([device] is the bonded
     * identity address; the controller resolves the iPhone's rotating addresses to it).
     */
    fun reconnect(device: BluetoothDevice) {
        scope.launch {
            if (!backgroundReconnectEnabled) {
                DiagnosticsLog.log("gatt: background reconnect disabled (debug strategy)")
                return@launch
            }
            val g = gatt
            if (g != null && g.device.address.equals(device.address, ignoreCase = true)) {
                Log.d(TAG, "Already connected/connecting to ${device.address}, skipping reconnect")
                return@launch
            }
            openGatt(device, autoConnect = true)
        }
    }

    private fun openGatt(device: BluetoothDevice, autoConnect: Boolean) {
        reconnectJob?.cancel()
        autoReconnect = true
        targetDevice = device
        serviceDiscoveryRetries = 0

        closeGatt()
        openedWithAutoConnect = autoConnect
        // A background request may wait for hours (iPhone away): don't show "Connecting…"
        _connectionState.value = if (autoConnect) {
            ConnectionState.Disconnected("Waiting for the iPhone")
        } else {
            ConnectionState.Connecting(displayName(device))
        }
        DiagnosticsLog.log("gatt: open ${device.address} autoConnect=$autoConnect")
        gatt = device.connectGatt(
            context,
            autoConnect, // true = background request via the controller's accept/resolving lists
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private fun handleConnected(g: BluetoothGatt) {
        if (g !== gatt) return
        linkUp = true
        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
        bondedSnapshot = bondedAddresses()
        _connectionState.value = ConnectionState.Connecting(displayName(g.device))
        DiagnosticsLog.log(
            "gatt: connected ${g.device.address} bond=${bondName(g.device.bondState)} " +
                "via=${if (openedWithAutoConnect) "background request" else "attach to inbound link"}"
        )

        scope.launch {
            // Higher MTU = fewer DS fragments. Failure is not fatal: ANCS works at 23.
            operations.enqueue("request MTU", OpKey.Mtu) { g.requestMtu(TARGET_MTU) }.await()
            discoverServices(g)
        }
    }

    private fun discoverServices(g: BluetoothGatt) {
        operations.enqueue("discover services", OpKey.DiscoverServices) { g.discoverServices() }
    }

    private fun handleDisconnected(g: BluetoothGatt, status: Int) {
        g.close()
        if (g !== gatt) return // stale callback from a GATT we already replaced
        DiagnosticsLog.log("gatt: disconnected ${g.device.address} status=$status")
        gatt = null
        clearSession()
        pendingBond?.complete(false)

        if (g.device.bondState == BluetoothDevice.BOND_BONDED && PairingPolicy.isKeyLossDisconnect(status)) {
            if (keyLoss.onSignal()) {
                enterRepairNeeded("iPhone rejected the watch's keys (status=$status)")
                return
            }
        }

        _connectionState.value = ConnectionState.Disconnected()
        if (autoReconnect && targetDevice != null) {
            scheduleReconnect()
        }
    }

    private fun handleServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (g !== gatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            DiagnosticsLog.log("gatt: service discovery failed status=$status, dropping link to retry")
            _connectionState.value = ConnectionState.Error("Service discovery failed")
            g.disconnect() // handleDisconnected schedules the reconnect
            return
        }

        val ancsService = g.getService(AncsConstants.ANCS_SERVICE_UUID)
        DiagnosticsLog.log(
            "gatt: ${g.services.size} services, ANCS=${ancsService != null} " +
                "AMS=${g.getService(AncsConstants.AMS_SERVICE_UUID) != null}"
        )

        if (ancsService == null) {
            if (g.device.bondState != BluetoothDevice.BOND_BONDED) {
                // Older iOS versions only publish ANCS to bonded accessories
                scope.launch {
                    if (ensureBonded(g) && gatt === g) discoverServices(g)
                }
                return
            }
            // Bonded but ANCS not visible: notification sharing off, or ANCS not published yet
            serviceDiscoveryRetries++
            if (serviceDiscoveryRetries <= MAX_SERVICE_DISCOVERY_RETRIES) {
                DiagnosticsLog.log("gatt: bonded but no ANCS, retry $serviceDiscoveryRetries/$MAX_SERVICE_DISCOVERY_RETRIES")
                scope.launch {
                    delay(2000)
                    if (gatt === g) discoverServices(g)
                }
            } else {
                DiagnosticsLog.log("gatt: no ANCS after retries — not an iPhone, or notification sharing is off")
                disconnectInternal()
                _connectionState.value = ConnectionState.Error("No ANCS: enable notification sharing on the iPhone")
            }
            return
        }

        // Already subscribed or subscribing (e.g. rediscovery) — don't reset the ANCS session
        if (isSubscribing || (notificationSourceChar != null && dataSourceChar != null)) {
            Log.i(TAG, "ANCS already subscribed, ignoring rediscovery")
            return
        }

        serviceDiscoveryRetries = 0
        val ns = ancsService.getCharacteristic(AncsConstants.NOTIFICATION_SOURCE_UUID)
        val cp = ancsService.getCharacteristic(AncsConstants.CONTROL_POINT_UUID)
        val ds = ancsService.getCharacteristic(AncsConstants.DATA_SOURCE_UUID)
        if (ns == null || ds == null || cp == null) {
            DiagnosticsLog.log("gatt: ANCS characteristics missing")
            _connectionState.value = ConnectionState.Error("ANCS characteristics missing")
            return
        }

        isSubscribing = true
        scope.launch { startAncsSession(g, ns, cp, ds) }
    }

    /**
     * ANCS characteristics "require authorization for access" (Apple spec): the link must be
     * bonded and encrypted before the CCCD writes succeed. Bond first, then subscribe
     * Data Source before Notification Source (Apple recommendation).
     */
    private suspend fun startAncsSession(
        g: BluetoothGatt,
        ns: BluetoothGattCharacteristic,
        cp: BluetoothGattCharacteristic,
        ds: BluetoothGattCharacteristic
    ) {
        try {
            if (!ensureBonded(g)) {
                if (gatt === g) {
                    _connectionState.value = ConnectionState.Error("Pairing was not accepted on the iPhone")
                    g.disconnect()
                }
                return
            }

            var subscribed = subscribe(g, ds)
            if (!subscribed && gatt === g && PairingPolicy.isAuthError(lastDescriptorStatus)) {
                // Bonded but the iPhone rejected the encrypted access: likely a stale bond
                DiagnosticsLog.log("ancs: subscription rejected with auth error $lastDescriptorStatus")
                if (keyLoss.onSignal()) {
                    enterRepairNeeded("iPhone rejected ANCS access (status=$lastDescriptorStatus)")
                    return
                }
            }
            subscribed = subscribed && subscribe(g, ns)
            if (gatt !== g) return
            if (!subscribed) {
                DiagnosticsLog.log("ancs: subscription failed (status=$lastDescriptorStatus), dropping link to retry")
                _connectionState.value = ConnectionState.Error("Could not subscribe to ANCS")
                g.disconnect()
                return
            }
            dataSourceChar = ds
            notificationSourceChar = ns
            controlPointChar = cp
            keyLoss.onSessionEstablished()
            DiagnosticsLog.log("ancs: SESSION ACTIVE (subscribed DS + NS)")
            _connectionState.value = ConnectionState.Connected(resolveDeviceName())
            readGapDeviceName(g)
        } finally {
            isSubscribing = false
        }
    }

    /** Bond with the device on this link if needed. Suspends until the user decides. */
    private suspend fun ensureBonded(g: BluetoothGatt): Boolean {
        val device = g.device
        if (device.bondState == BluetoothDevice.BOND_BONDED) return true

        val deferred = CompletableDeferred<Boolean>()
        pendingBond = deferred
        _connectionState.value = ConnectionState.Bonding(displayName(device))
        DiagnosticsLog.log("bond: requesting pairing with ${device.address} (accept on the iPhone)")

        val started = device.bondState == BluetoothDevice.BOND_BONDING || device.createBond()
        // The bond may have completed between the check and registering the deferred
        if (device.bondState == BluetoothDevice.BOND_BONDED) deferred.complete(true)
        if (!started && !deferred.isCompleted) {
            DiagnosticsLog.log("bond: createBond() refused")
            pendingBond = null
            return false
        }

        val bonded = withTimeoutOrNull(BOND_TIMEOUT_MS) { deferred.await() } ?: run {
            DiagnosticsLog.log("bond: timed out waiting for the iPhone")
            false
        }
        pendingBond = null
        if (bonded) recordNewBond(device)
        return bonded
    }

    /** Work out the identity address the new bond created and report it. */
    private fun recordNewBond(device: BluetoothDevice) {
        val identity = PairingPolicy.newlyBondedAddress(bondedSnapshot, bondedAddresses())
            ?: device.address.also {
                DiagnosticsLog.log("bond: couldn't single out the new bond, using link address $it")
            }
        bondedSnapshot = bondedAddresses()
        DiagnosticsLog.log("bond: saved bond address $identity (Android's stable handle for this bond; link address ${device.address})")
        adapter?.getRemoteDevice(identity)?.let { targetDevice = it }
        onPhoneBonded?.invoke(identity, device.name)
    }

    private suspend fun subscribe(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        val descriptor = characteristic.getDescriptor(AncsConstants.CCCD_UUID) ?: run {
            Log.e(TAG, "CCCD not found for ${characteristic.uuid}")
            return false
        }
        if (!g.setCharacteristicNotification(characteristic, true)) {
            Log.e(TAG, "setCharacteristicNotification failed for ${characteristic.uuid}")
            return false
        }
        return operations.enqueue("subscribe ${characteristic.uuid}", OpKey.WriteDescriptor(characteristic.uuid)) {
            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        }.await()
    }

    /**
     * Android 16 told us the bonded iPhone could not be authenticated (ACTION_KEY_MISSING):
     * the iPhone forgot the watch. The system keeps the bond and shows its own dialog; stop
     * retrying and ask the user to pair again.
     */
    fun onKeyMissing(address: String) {
        scope.launch {
            val ours = targetDevice?.address.equals(address, ignoreCase = true) ||
                gatt?.device?.address.equals(address, ignoreCase = true)
            DiagnosticsLog.log("bond: KEY_MISSING for $address (ours=$ours)")
            if (ours) enterRepairNeeded("Android reported the bond as lost")
        }
    }

    private fun enterRepairNeeded(reason: String) {
        DiagnosticsLog.log("bond: REPAIR NEEDED — $reason")
        disconnectInternal()
        _connectionState.value = ConnectionState.RepairNeeded(reason)
    }

    /**
     * Write to the ANCS Control Point characteristic.
     * Suspends until the write is acknowledged; returns false if not connected,
     * rejected, failed or timed out.
     */
    suspend fun writeControlPoint(data: ByteArray): Boolean {
        val result = withContext(bleDispatcher) {
            val g = gatt
            val char = controlPointChar
            if (g == null || char == null) return@withContext null
            operations.enqueue("control point write", OpKey.WriteCharacteristic(char.uuid)) {
                g.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            }
        } ?: return false
        return result.await()
    }

    private fun displayName(device: BluetoothDevice): String =
        cachedDeviceName ?: device.name?.takeIf { it.isNotBlank() } ?: "iPhone"

    private fun resolveDeviceName(): String = gatt?.device?.let { displayName(it) } ?: "iPhone"

    /**
     * Read the real device name from the GAP (Generic Access Profile) service.
     * The Device Name characteristic (0x2A00) contains the name set on the iPhone.
     */
    private fun readGapDeviceName(g: BluetoothGatt) {
        val char = g.getService(GAP_SERVICE_UUID)?.getCharacteristic(GAP_DEVICE_NAME_UUID) ?: return
        operations.enqueue("read GAP name", OpKey.ReadCharacteristic(char.uuid)) {
            g.readCharacteristic(char)
        }
    }

    private fun onCharacteristicRead(characteristicUuid: UUID, value: ByteArray) {
        if (characteristicUuid != GAP_DEVICE_NAME_UUID) return
        val name = String(value, Charsets.UTF_8)
        if (name.isNotBlank()) {
            cachedDeviceName = name
            DiagnosticsLog.log("gatt: iPhone name \"$name\"")
            if (_connectionState.value is ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Connected(name)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
            _connectionState.value = ConnectionState.Idle
        }
    }

    private fun disconnectInternal() {
        autoReconnect = false
        reconnectJob?.cancel()
        targetDevice = null
        pendingBond?.complete(false)
        closeGatt()
    }

    private fun closeGatt() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        clearSession()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.i(TAG, "Scheduling reconnect in ${reconnectDelay}ms")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            val device = targetDevice ?: return@launch
            // Only a bonded identity address can be reconnected to in the background: an
            // unbonded private address will have rotated. Unbonded → wait for the iPhone.
            if (device.bondState == BluetoothDevice.BOND_BONDED && backgroundReconnectEnabled) {
                openGatt(device, autoConnect = true)
            } else {
                DiagnosticsLog.log("gatt: not bonded yet; waiting for the iPhone to connect again")
            }
        }
    }

    private fun clearSession() {
        linkUp = false
        operations.clear()
        notificationSourceChar = null
        controlPointChar = null
        dataSourceChar = null
        isSubscribing = false
    }

    private fun bondName(state: Int) = when (state) {
        BluetoothDevice.BOND_BONDED -> "BONDED"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        else -> "NONE"
    }

    fun destroy() {
        // Runs on the caller's thread: the scope is cancelled right after, so nothing
        // can race with this teardown.
        scope.cancel()
        autoReconnect = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
