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
import app.tandemwear.watch.model.ConnectionState
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
import java.util.UUID

/**
 * Manages the BLE GATT connection to an iPhone and the ANCS subscription lifecycle.
 *
 * Flow: connect → MTU → discover services → (bond) → subscribe DS → subscribe NS → active
 *
 * Threading: all mutable state lives on [bleDispatcher] (one thread at a time). GATT
 * callbacks arrive on binder threads and are hopped onto it; every GATT operation goes
 * through [GattOperationQueue] so only one is in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("MissingPermission")
class BleConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "BleConnectionManager"
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 3_000L
        private const val TARGET_MTU = 512
        private const val MAX_SERVICE_DISCOVERY_RETRIES = 2
        private const val EVENT_BUFFER_CAPACITY = 256

        private val GAP_SERVICE_UUID: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val GAP_DEVICE_NAME_UUID: UUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")
    }

    private val bleDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + bleDispatcher)
    private val operations = GattOperationQueue(scope)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _notificationSourceEvents = MutableSharedFlow<ByteArray>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val notificationSourceEvents: SharedFlow<ByteArray> = _notificationSourceEvents.asSharedFlow()

    private val _dataSourceEvents = MutableSharedFlow<ByteArray>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val dataSourceEvents: SharedFlow<ByteArray> = _dataSourceEvents.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var autoReconnect = true

    private var serviceDiscoveryRetries = 0
    private var notificationSourceChar: BluetoothGattCharacteristic? = null
    private var controlPointChar: BluetoothGattCharacteristic? = null
    private var dataSourceChar: BluetoothGattCharacteristic? = null
    private var isSubscribing = false

    /** Cached real device name read from GAP characteristic */
    private var cachedDeviceName: String? = null

    val bondStateReceiver = BondStateReceiver(
        onBonded = { _ ->
            scope.launch {
                Log.i(TAG, "Bond completed, re-discovering services")
                // Small delay after bonding before service discovery
                delay(1000)
                gatt?.let { discoverServices(it) }
            }
        },
        onBondFailed = { _ ->
            scope.launch {
                Log.e(TAG, "Bond failed")
                disconnectInternal()
                _connectionState.value = ConnectionState.Error("Pairing failed")
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
                Log.i(TAG, "MTU negotiated: $mtu (status=$status)")
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
     * Connect to a BLE device (iPhone).
     */
    fun connect(device: BluetoothDevice, autoReconnect: Boolean = true) {
        scope.launch {
            openGatt(device, autoConnect = false, autoReconnect = autoReconnect)
        }
    }

    /**
     * Reconnect to a previously bonded device using autoConnect.
     */
    fun reconnect(device: BluetoothDevice) {
        scope.launch {
            openGatt(device, autoConnect = true, autoReconnect = true)
        }
    }

    private fun openGatt(device: BluetoothDevice, autoConnect: Boolean, autoReconnect: Boolean) {
        // Guard: skip if already connected or connecting to this device
        val currentState = _connectionState.value
        if (gatt != null && gatt?.device?.address == device.address &&
            (currentState is ConnectionState.Connected || currentState is ConnectionState.Connecting)) {
            Log.d(TAG, "Already connected/connecting to ${device.address}, skipping")
            return
        }

        reconnectJob?.cancel()
        this.autoReconnect = autoReconnect
        this.targetDevice = device
        this.serviceDiscoveryRetries = 0

        _connectionState.value = ConnectionState.Connecting(displayName(device))

        closeGatt()
        gatt = device.connectGatt(
            context,
            autoConnect, // true = chipset whitelist, battery efficient; false = faster first connection
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private fun handleConnected(g: BluetoothGatt) {
        if (g !== gatt) return
        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
        _connectionState.value = ConnectionState.Connecting(displayName(g.device))

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
        Log.i(TAG, "Disconnected, status=$status")
        g.close()
        if (g !== gatt) return // stale callback from a GATT we already replaced
        gatt = null
        clearSession()
        _connectionState.value = ConnectionState.Disconnected()

        if (autoReconnect && targetDevice != null) {
            scheduleReconnect()
        }
    }

    private fun handleServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (g !== gatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "Service discovery failed: $status — dropping link to retry")
            _connectionState.value = ConnectionState.Error("Service discovery failed")
            g.disconnect() // handleDisconnected schedules the reconnect
            return
        }

        val ancsService = g.getService(AncsConstants.ANCS_SERVICE_UUID)
        if (ancsService == null) {
            // ANCS not visible — likely not bonded yet
            if (g.device.bondState != BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, "ANCS not found, initiating bonding")
                _connectionState.value = ConnectionState.Bonding(displayName(g.device))
                g.device.createBond()
            } else {
                // Bonded but ANCS still not visible — retry, then give up
                // (this device may not be an iPhone, or notification sharing is off)
                serviceDiscoveryRetries++
                if (serviceDiscoveryRetries <= MAX_SERVICE_DISCOVERY_RETRIES) {
                    Log.w(TAG, "Bonded but ANCS not found, retry $serviceDiscoveryRetries/$MAX_SERVICE_DISCOVERY_RETRIES")
                    scope.launch {
                        delay(2000)
                        if (gatt === g) discoverServices(g)
                    }
                } else {
                    Log.e(TAG, "ANCS not found after $MAX_SERVICE_DISCOVERY_RETRIES retries — not an iPhone?")
                    disconnectInternal()
                    _connectionState.value = ConnectionState.Error("Not an iPhone (no ANCS)")
                }
            }
            return
        }

        // Already subscribed or subscribing (e.g. rediscovery) — don't reset the ANCS session
        if (isSubscribing || (notificationSourceChar != null && dataSourceChar != null)) {
            Log.i(TAG, "ANCS already subscribed, ignoring rediscovery")
            return
        }

        Log.i(TAG, "ANCS service found")
        serviceDiscoveryRetries = 0
        val ns = ancsService.getCharacteristic(AncsConstants.NOTIFICATION_SOURCE_UUID)
        val cp = ancsService.getCharacteristic(AncsConstants.CONTROL_POINT_UUID)
        val ds = ancsService.getCharacteristic(AncsConstants.DATA_SOURCE_UUID)
        if (ns == null || ds == null || cp == null) {
            Log.e(TAG, "Missing ANCS characteristics")
            _connectionState.value = ConnectionState.Error("ANCS characteristics missing")
            return
        }

        isSubscribing = true
        scope.launch {
            // Subscribe to Data Source FIRST (Apple recommendation), then Notification Source
            val subscribed = subscribe(g, ds) && subscribe(g, ns)
            isSubscribing = false
            if (gatt !== g) return@launch
            if (!subscribed) {
                Log.e(TAG, "ANCS subscription failed — dropping link to retry")
                _connectionState.value = ConnectionState.Error("Could not subscribe to ANCS")
                g.disconnect()
                return@launch
            }
            dataSourceChar = ds
            notificationSourceChar = ns
            controlPointChar = cp
            Log.i(TAG, "Subscribed to Notification Source — ANCS session active!")
            _connectionState.value = ConnectionState.Connected(resolveDeviceName())
            readGapDeviceName(g)
        }
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

    private fun displayName(device: BluetoothDevice): String {
        val name = device.name ?: ""
        return if (name.isEmpty() || isTruncatedBleName(name)) "iPhone" else name
    }

    /**
     * Resolve a user-friendly device name from the bonded device list or GATT connection.
     * BLE advertised names are often truncated (e.g., "AncsBrid" instead of "iPhone").
     */
    private fun resolveDeviceName(): String {
        // Return cached GAP name if available
        cachedDeviceName?.let { return it }

        // Check bonded devices for a full name
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val bondedName = adapter?.bondedDevices?.firstOrNull { device ->
            device.address == gatt?.device?.address
        }?.name

        // Prefer the bonded name, fall back to GATT device name
        val rawName = bondedName ?: gatt?.device?.name ?: "iPhone"

        // If the name is a truncated BLE name, show "iPhone" instead
        return if (isTruncatedBleName(rawName)) "iPhone" else rawName
    }

    private fun isTruncatedBleName(name: String): Boolean {
        return name.startsWith("AncsBrid", ignoreCase = true) ||
            name.startsWith("AppleToWe", ignoreCase = true) ||
            name.startsWith("WearBrid", ignoreCase = true) ||
            name.startsWith("WearBridge", ignoreCase = true)
    }

    /**
     * Read the real device name from the GAP (Generic Access Profile) service.
     * The Device Name characteristic (0x2A00) contains the actual iPhone name
     * rather than the truncated BLE advertising name.
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
        if (name.isNotBlank() && !isTruncatedBleName(name)) {
            cachedDeviceName = name
            Log.i(TAG, "GAP Device Name: $name")
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

    /**
     * Get the bonded iPhone device if one exists.
     */
    fun getBondedIPhone(): BluetoothDevice? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return null
        return adapter.bondedDevices?.firstOrNull { device ->
            val name = device.name ?: ""
            name.contains("iPhone", ignoreCase = true) ||
                name.contains("AncsBrid", ignoreCase = true) ||
                name.contains("AncsBridge", ignoreCase = true) ||
                name.contains("AppleToWe", ignoreCase = true) ||
                name.contains("AppleToWearOS", ignoreCase = true) ||
                name.contains("WearBridge", ignoreCase = true) ||
                name.contains("WearBrid", ignoreCase = true)
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.i(TAG, "Scheduling reconnect in ${reconnectDelay}ms")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            targetDevice?.let { openGatt(it, autoConnect = true, autoReconnect = true) }
        }
    }

    private fun clearSession() {
        operations.clear()
        notificationSourceChar = null
        controlPointChar = null
        dataSourceChar = null
        isSubscribing = false
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
