package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.tandemwear.watch.ble.GattOperationQueue.OpKey
import app.tandemwear.watch.diag.DiagnosticsLog
import app.tandemwear.watch.diag.LinkStats
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    /** Current bonded addresses, to find which address a new bond created. */
    private val bondedAddresses: () -> Set<String>,
    /** Bond address of the paired iPhone, if any (a missing ANCS is then never terminal). */
    private val pairedAddress: () -> String?
) {

    companion object {
        private const val TAG = "BleConnectionManager"
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 3_000L
        private const val TARGET_MTU = 512
        private const val EVENT_BUFFER_CAPACITY = 256
        /** The user has to accept the pairing prompt on the iPhone. */
        private const val BOND_TIMEOUT_MS = 90_000L
        /** A pending autoConnect client normally picks up an inbound link by itself. */
        private const val INBOUND_PICKUP_GRACE_MS = 2_000L
        private const val PREFS_LINK_STATS = "link_stats"
        private const val KEY_LINK_STATS = "stats"

        private val GAP_SERVICE_UUID: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val GAP_DEVICE_NAME_UUID: UUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")
    }

    private val bleDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + bleDispatcher)
    private val operations = GattOperationQueue(scope)
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _notificationSourceEvents = MutableSharedFlow<AncsSessionPacket>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    /** Notification Source events tagged with the ANCS session they belong to. */
    val notificationSourceEvents: SharedFlow<AncsSessionPacket> = _notificationSourceEvents.asSharedFlow()

    /**
     * Session token NS events are tagged with. Set before subscribing Notification Source,
     * so events that race ahead of the Connected state still carry the new session.
     */
    @Volatile
    private var currentSessionId = 0L

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
    /** When the current link came up (elapsedRealtime), to report how long ANCS took. */
    private var linkUpAtMs = 0L
    /** When the current GATT client was opened (elapsedRealtime), to infer who started the link. */
    private var requestOpenedAtMs = 0L
    /** Diagnostics only: who started the current link and whether ANCS came up on it. */
    private var linkInitiator: LinkStats.Initiator? = null
    private var linkHadAncs = false
    /** Completed by encryption change / Service Changed to cut an ANCS wait short. */
    private var ancsWait: CompletableDeferred<String>? = null
    private var ancsWaitJob: Job? = null
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
            if (!_notificationSourceEvents.tryEmit(AncsSessionPacket(currentSessionId, data))) {
                Log.e(TAG, "NS event dropped: buffer full")
            }
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
        },
        onServiceChangedCallback = { g ->
            scope.launch {
                if (g !== gatt) return@launch
                DiagnosticsLog.log("gatt: Service Changed received")
                signalAncsWait("service changed")
                // While waiting for ANCS the wait loop re-discovers; otherwise do it now
                if (ancsWait == null) discoverServices(g)
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
        requestOpenedAtMs = SystemClock.elapsedRealtime()
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
        linkUpAtMs = SystemClock.elapsedRealtime()
        linkInitiator = LinkStats.inferInitiator(openedWithAutoConnect, linkUpAtMs - requestOpenedAtMs)
        linkHadAncs = false
        _connectionState.value = ConnectionState.Connecting(displayName(g.device))
        DiagnosticsLog.log(
            "gatt: connected ${g.device.address} bond=${bondName(g.device.bondState)} " +
                "via=${if (openedWithAutoConnect) "background request" else "attach to inbound link"} " +
                "initiator=${linkInitiator?.name?.lowercase()} (request opened ${linkUpAtMs - requestOpenedAtMs} ms before)"
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
            if (notificationSourceChar != null) {
                // ANCS was unpublished under an active session (Service Changed): session over
                DiagnosticsLog.log("ancs: ANCS disappeared from the iPhone, session ended")
                clearAncsCharacteristics()
                _connectionState.value = ConnectionState.Connecting(displayName(g.device))
            }
            waitForAncs(g)
            return
        }

        // Already subscribed or subscribing (e.g. rediscovery) — don't reset the ANCS session
        if (isSubscribing || (notificationSourceChar != null && dataSourceChar != null)) {
            Log.i(TAG, "ANCS already subscribed, ignoring rediscovery")
            return
        }

        DiagnosticsLog.log(
            "ancs: ANCS visible ${SystemClock.elapsedRealtime() - linkUpAtMs} ms after link up " +
                "(discovery retries: $serviceDiscoveryRetries)"
        )
        cancelAncsWait()
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
            // New ANCS session: the iPhone renumbers UIDs, so tag what follows with a new token
            currentSessionId = SystemClock.elapsedRealtimeNanos()
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
            linkHadAncs = true
            DiagnosticsLog.log(
                "ancs: SESSION ACTIVE (subscribed DS + NS) ${SystemClock.elapsedRealtime() - linkUpAtMs} ms after link up"
            )
            _connectionState.value = ConnectionState.Connected(resolveDeviceName(), currentSessionId)
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
            val state = _connectionState.value
            if (state is ConnectionState.Connected) {
                _connectionState.value = state.copy(deviceName = name)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
            _connectionState.value = ConnectionState.Idle
        }
    }

    /**
     * Diagnostics: one line per finished link plus today's totals, persisted per day.
     * No effect on connection behavior.
     */
    private fun recordLinkStats() {
        val initiator = linkInitiator ?: return
        linkInitiator = null
        val prefs = context.getSharedPreferences(PREFS_LINK_STATS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val stats = (LinkStats.decode(prefs.getString(KEY_LINK_STATS, null)) ?: LinkStats(today))
            .record(today, initiator, linkHadAncs)
        prefs.edit().putString(KEY_LINK_STATS, stats.encode()).apply()
        val seconds = (SystemClock.elapsedRealtime() - linkUpAtMs) / 1000
        DiagnosticsLog.log(
            "link: ended initiator=${initiator.name.lowercase()} ancs=$linkHadAncs after ${seconds}s | " +
                "today ${stats.day}: ${stats.summary()}"
        )
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
        if (linkUp) recordLinkStats()
        linkUp = false
        operations.clear()
        cancelAncsWait()
        clearAncsCharacteristics()
        isSubscribing = false
    }

    private fun clearAncsCharacteristics() {
        notificationSourceChar = null
        controlPointChar = null
        dataSourceChar = null
        currentSessionId = 0L
    }

    /**
     * Bonded link without ANCS (yet). After a reconnect iOS publishes ANCS only once the link
     * is encrypted with the bond's keys, so wait — progressively longer — and re-discover as
     * soon as Android reports encryption or a Service Changed, or when the wait expires.
     * A paired iPhone is never a terminal error: the link is dropped and reconnection retries.
     */
    private fun waitForAncs(g: BluetoothGatt) {
        val retry = serviceDiscoveryRetries
        val waitMs = AncsDiscoveryPolicy.delayBeforeRetry(retry)
        val elapsed = SystemClock.elapsedRealtime() - linkUpAtMs
        if (waitMs == null) {
            val paired = pairedAddress()?.equals(g.device.address, ignoreCase = true) == true
            when (AncsDiscoveryPolicy.onExhausted(paired)) {
                AncsDiscoveryPolicy.OnExhausted.RECONNECT_LATER -> {
                    DiagnosticsLog.log("ancs: still no ANCS ${elapsed} ms after link up — dropping link, will reconnect")
                    _connectionState.value = ConnectionState.Disconnected("Waiting for the iPhone")
                    g.disconnect() // handleDisconnected keeps autoReconnect and schedules it
                }
                AncsDiscoveryPolicy.OnExhausted.REPORT_ERROR -> {
                    DiagnosticsLog.log("ancs: no ANCS after retries — not an iPhone, or notification sharing is off")
                    disconnectInternal()
                    _connectionState.value = ConnectionState.Error("No ANCS: enable notification sharing on the iPhone")
                }
            }
            return
        }

        serviceDiscoveryRetries++
        // State only (B5): what the UI shows. Retries and reconnection are unchanged.
        if (AncsDiscoveryPolicy.showLinkedWithoutAncs(elapsed) && _connectionState.value is ConnectionState.Connecting) {
            _connectionState.value = ConnectionState.LinkedWithoutAncs(displayName(g.device))
        }
        DiagnosticsLog.log(
            "ancs: bonded but no ANCS yet (${g.services.size} services, ${elapsed} ms after link up); " +
                "waiting up to $waitMs ms for encryption/Service Changed (retry ${retry + 1}/${AncsDiscoveryPolicy.RETRY_DELAYS_MS.size})"
        )
        cancelAncsWait()
        val wait = CompletableDeferred<String>()
        ancsWait = wait
        ancsWaitJob = scope.launch {
            val trigger = withTimeoutOrNull(waitMs) { wait.await() } ?: "timer"
            if (ancsWait === wait) ancsWait = null
            if (gatt !== g || !linkUp) return@launch
            if (AncsDiscoveryPolicy.shouldRefreshCache(retry)) {
                DiagnosticsLog.log("gatt: refreshing Android's GATT cache: ${refreshGattCache(g)}")
            }
            DiagnosticsLog.log("gatt: re-discovering services (trigger: $trigger)")
            discoverServices(g)
        }
    }

    private fun signalAncsWait(trigger: String) {
        ancsWait?.complete(trigger)
    }

    private fun cancelAncsWait() {
        ancsWaitJob?.cancel()
        ancsWaitJob = null
        ancsWait = null
    }

    /**
     * Android caches the GATT database of bonded devices; a cache taken before encryption
     * can miss ANCS. BluetoothGatt.refresh() is not public API (non-SDK, reflection), so this
     * is best effort and the result is logged.
     */
    private fun refreshGattCache(g: BluetoothGatt): Boolean = try {
        g.javaClass.getMethod("refresh").invoke(g) as? Boolean ?: false
    } catch (e: Exception) {
        false
    }

    /**
     * Android reported an encryption change on a link (Android 16 ACTION_ENCRYPTION_CHANGE;
     * delivery depends on target SDK and OEM). Encrypted = the bond is in use, so ANCS
     * should now be published: re-discover right away.
     */
    fun onEncryptionChanged(address: String, encrypted: Boolean) {
        scope.launch {
            if (!gatt?.device?.address.equals(address, ignoreCase = true)) return@launch
            DiagnosticsLog.log("gatt: encryption ${if (encrypted) "ON" else "OFF"} for $address")
            if (encrypted) signalAncsWait("encryption on")
        }
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

/** A Notification Source payload and the ANCS session it arrived in. */
class AncsSessionPacket(val sessionId: Long, val data: ByteArray)
