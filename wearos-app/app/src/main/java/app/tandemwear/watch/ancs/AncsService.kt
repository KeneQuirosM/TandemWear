package app.tandemwear.watch.ancs

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.os.IBinder
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import app.tandemwear.watch.AncsApplication
import app.tandemwear.watch.R
import app.tandemwear.watch.ams.MusicSession
import app.tandemwear.watch.ams.RemoteCommand
import app.tandemwear.watch.ble.AmsPacket
import app.tandemwear.watch.ble.AncsConstants
import app.tandemwear.watch.ble.BleConnectionManager
import app.tandemwear.watch.ble.InboundLinkMonitor
import app.tandemwear.watch.ble.PairingPolicy
import app.tandemwear.watch.ble.WatchAdvertiser
import app.tandemwear.watch.data.PairedPhoneStore
import app.tandemwear.watch.data.history.HistoryEntry
import app.tandemwear.watch.data.history.HistoryRepository
import app.tandemwear.watch.data.history.TandemDatabase
import app.tandemwear.watch.data.settings.SettingsRepository
import app.tandemwear.watch.data.settings.TandemSettings
import app.tandemwear.watch.notify.NotificationPolicy
import app.tandemwear.watch.tile.SurfaceState
import app.tandemwear.watch.diag.DiagnosticsLog
import app.tandemwear.watch.ble.BondStateReceiver
import app.tandemwear.watch.model.AncsEvent
import app.tandemwear.watch.model.AncsNotification
import app.tandemwear.watch.model.ConnectionState
import app.tandemwear.watch.ui.IncomingCallActivity
import app.tandemwear.watch.ui.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that manages the BLE link to the iPhone and the ANCS pipeline.
 *
 * Link layer: the watch advertises with ANCS service solicitation ([WatchAdvertiser]); the
 * user pairs from iPhone › Settings › Bluetooth; the iPhone connects to the watch
 * ([InboundLinkMonitor]) and [BleConnectionManager] runs ANCS as GATT client over that
 * link. Once bonded, reconnection uses both the iPhone reconnecting to the advertising
 * watch and a background autoConnect request to the bonded identity address.
 *
 * Pipeline: NS events → fetch attributes via CP → parse DS response → post Android notification
 */
@SuppressLint("MissingPermission")
class AncsService : Service() {

    companion object {
        private const val TAG = "AncsService"
        private const val SERVICE_NOTIFICATION_ID = 1

        const val ACTION_START = "app.tandemwear.watch.START"
        const val ACTION_STOP = "app.tandemwear.watch.STOP"
        const val ACTION_START_PAIRING = "app.tandemwear.watch.START_PAIRING"
        const val ACTION_DISCONNECT = "app.tandemwear.watch.DISCONNECT"
        const val ACTION_RECONNECT = "app.tandemwear.watch.RECONNECT"
        const val ACTION_PERFORM_NOTIFICATION_ACTION = "app.tandemwear.watch.PERFORM_ACTION"
        const val ACTION_CLEAR_ALL = "app.tandemwear.watch.CLEAR_ALL"
        const val EXTRA_ACTION_ID = "action_id"
        /** Music (AMS): send a RemoteCommand id ([EXTRA_MEDIA_COMMAND]) to the iPhone. */
        const val ACTION_MEDIA_COMMAND = "app.tandemwear.watch.MEDIA_COMMAND"
        const val EXTRA_MEDIA_COMMAND = "media_command"
        /** Music (AMS): the player screen or Tile needs data ([EXTRA_MUSIC_DEMANDED]); ON_DEMAND mode. */
        const val ACTION_MUSIC_DEMAND = "app.tandemwear.watch.MUSIC_DEMAND"
        const val EXTRA_MUSIC_DEMANDED = "music_demanded"
        const val NOTIFICATION_ID_CALL = 999
        private const val MAX_ACTIVE_NOTIFICATIONS = 20 // Android limit is 25; keep headroom
        // Max wait for a full Data Source response before moving to the next request
        private const val ATTRIBUTE_RESPONSE_TIMEOUT_MS = 3_000L
        // How long fast "pairing" advertising lasts before falling back
        private const val PAIRING_WINDOW_MS = 3 * 60_000L
        // Quiet time after the last skipped backlog event before logging the summary
        private const val BACKLOG_SUMMARY_DELAY_MS = 2_000L
        private const val PREFS_NOTIFICATION_IDS = "notification_ids"
        private const val KEY_NEXT_ID = "next_id"
        private const val PREFS_HISTORY = "history"
        private const val SURFACE_UPDATE_DELAY_MS = 1_500L
        /** Bond address of the iPhone the stored history belongs to. */
        private const val KEY_HISTORY_OWNER = "owner_address"

        /** Shared connection state observable by the ViewModel */
        private val _sharedConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

        private val _sharedMusic = MutableStateFlow(MusicSession.NONE)
        /** The iPhone's music for the current ANCS session (AMS). NONE without a session. */
        val sharedMusic: StateFlow<MusicSession> = _sharedMusic.asStateFlow()
        val sharedConnectionState: StateFlow<ConnectionState> = _sharedConnectionState.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var connectionManager: BleConnectionManager
    private lateinit var advertiser: WatchAdvertiser
    private lateinit var inboundMonitor: InboundLinkMonitor
    private lateinit var pairedPhoneStore: PairedPhoneStore
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator
    private lateinit var history: HistoryRepository
    private var surfaceUpdateJob: Job? = null

    // Restored-notification check (B2): wall-clock start of the ANCS session, and whether the
    // clocks/zones looked unreliable during it (then everything counts as new)
    private var sessionStartedAtMs = 0L
    private var sessionClockDoubt = false
    private lateinit var seenNotifications: SeenNotifications

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!sessionClockDoubt) DiagnosticsLog.log("ancs: ${intent.action?.substringAfterLast('.')} — restored-notification check off for this session")
            sessionClockDoubt = true
        }
    }

    // Latest user settings; the flow below keeps it current, so changes apply live
    @Volatile private var settings = TandemSettings()

    private val dataSourceAssembler = DataSourceAssembler()
    private val appNameCache = mutableMapOf<String, String>()

    // Track active notification IDs in posting order (oldest first) for auto-cleanup
    private val activeNotificationIds = ArrayDeque<Int>()

    private var activeCallUid: Long = -1

    // ANCS UID → Android id, per session (UIDs are renumbered on every reconnect)
    private lateinit var idRegistry: NotificationIdRegistry

    // Backlog (PreExisting) events skipped in this session, logged as one summary line
    private var skippedBacklogCount = 0
    private var backlogSummaryJob: Job? = null

    // Pairing window: fast advertising, and unbonded iPhones may attach
    private var pairingWindowJob: Job? = null
    private val pairingWindowOpen: Boolean get() = pairingWindowJob?.isActive == true

    // Diagnostics: log the first real (non-backlog) notification of each service run
    private var firstNotificationLogged = false

    // Serial queue for Control Point requests
    private val attributeRequestQueue = Channel<AttributeRequest>(Channel.BUFFERED)
    /** Requests queued or in progress; AMS (music) only uses the GATT queue while this is 0. */
    private val attributeRequestsPending = java.util.concurrent.atomic.AtomicInteger(0)
    private var requestProcessorJob: Job? = null

    // Completed by handleDataSourceEvent when the in-flight request's response is assembled
    private var pendingAttributeResponse: CompletableDeferred<Unit>? = null

    private data class AttributeRequest(
        val sessionId: Long,
        val event: AncsEvent,
        val requestedAttributes: List<Int>
    )

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    DiagnosticsLog.log("bluetooth: ON")
                    startLinkLayer()
                }
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    DiagnosticsLog.log("bluetooth: turning OFF")
                    advertiser.stop()
                    inboundMonitor.close()
                }
            }
        }
    }

    private val keyMissingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            connectionManager.onKeyMissing(device.address)
        }
    }

    private val encryptionChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            val enabled = intent.getBooleanExtra(BondStateReceiver.EXTRA_ENCRYPTION_ENABLED, false)
            connectionManager.onEncryptionChanged(device.address, enabled)
        }
    }

    override fun onCreate() {
        super.onCreate()
        pairedPhoneStore = PairedPhoneStore(this)
        logServiceStart()
        connectionManager = BleConnectionManager(
            this,
            bondedAddresses = { pairedPhoneStore.bondedAddresses() },
            pairedAddress = { pairedPhoneStore.pairedAddress }
        )
        val idPrefs = getSharedPreferences(PREFS_NOTIFICATION_IDS, MODE_PRIVATE)
        idRegistry = NotificationIdRegistry(
            initialNextId = idPrefs.getInt(KEY_NEXT_ID, NotificationIdRegistry.FIRST_ID),
            onNextIdChanged = { idPrefs.edit().putInt(KEY_NEXT_ID, it).apply() }
        )
        advertiser = WatchAdvertiser(this)
        inboundMonitor = InboundLinkMonitor(
            this,
            onLinkUp = { device -> scope.launch { onInboundLink(device) } },
            onLinkDown = { _, _ -> }
        )
        notificationManager = getSystemService(NotificationManager::class.java)
        vibrator = getSystemService(Vibrator::class.java)
        history = HistoryRepository(TandemDatabase.get(this).historyDao())
        seenNotifications = SeenNotifications(java.io.File(filesDir, "seen_notifications.txt"))
        scope.launch {
            SettingsRepository(this@AncsService).settings.collect {
                settings = it
                // Applied live: subscribes to AMS now, or stops its notifications
                connectionManager.setMusicEnabled(it.musicEnabled)
            }
        }

        connectionManager.ancsRequestsPending = { attributeRequestsPending.get() }

        connectionManager.onPhoneBonded = { identity, name ->
            scope.launch {
                pairedPhoneStore.save(identity, name)
                onPhonePaired(identity)
                DiagnosticsLog.log("pairing: saved iPhone $identity")
                endPairingWindow()
            }
        }

        registerReceiver(connectionManager.bondStateReceiver, BondStateReceiver.intentFilter)
        registerReceiver(adapterStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(keyMissingReceiver, IntentFilter(BondStateReceiver.ACTION_KEY_MISSING))
        registerReceiver(encryptionChangeReceiver, IntentFilter(BondStateReceiver.ACTION_ENCRYPTION_CHANGE))
        registerReceiver(timeChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        })

        // Register PhoneAccount for Telecom framework integration
        // This lets us show incoming calls via the system call UI
        AncsConnectionService.registerPhoneAccount(this)

        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification(getString(R.string.service_status_starting)))

        // Listen for Notification Source events
        scope.launch {
            connectionManager.notificationSourceEvents.collect { packet ->
                ensureAncsSession(packet.sessionId)
                handleNotificationSourceEvent(packet.data)
            }
        }

        // Music (AMS): fold packets of the current session into the shared state
        scope.launch {
            connectionManager.amsEvents.collect { packet ->
                if (!idRegistry.isCurrent(packet.sessionId)) return@collect
                val now = android.os.SystemClock.elapsedRealtime()
                val before = _sharedMusic.value
                _sharedMusic.value = when (packet) {
                    is AmsPacket.Status -> _sharedMusic.value.onStatus(packet.available, packet.subscribed)
                    is AmsPacket.EntityUpdate -> _sharedMusic.value.onEntityUpdate(packet.data, now)
                    is AmsPacket.SupportedCommands -> _sharedMusic.value.onSupportedCommands(packet.data)
                    is AmsPacket.FullValue -> _sharedMusic.value.onFullValue(packet.entity, packet.attribute, packet.value, now)
                }
                // Tile music card: refresh only when what it shows changes (not every position update)
                val after = _sharedMusic.value
                if (before.hasPlayer != after.hasPlayer || before.media.isPlaying != after.media.isPlaying ||
                    before.media.title != after.media.title || before.media.artist != after.media.artist
                ) {
                    requestSurfaceUpdate()
                }
            }
        }

        // Listen for Data Source events
        scope.launch {
            connectionManager.dataSourceEvents.collect { data ->
                handleDataSourceEvent(data)
            }
        }

        // Update service notification, shared state and advertising based on connection state
        scope.launch {
            connectionManager.connectionState.collectLatest { state ->
                val shown = if (state is ConnectionState.Idle && pairingWindowOpen) {
                    ConnectionState.WaitingForIphone(advertiser.advertisedName)
                } else {
                    state
                }
                _sharedConnectionState.value = shown
                updateAdvertising(state)
                if (state is ConnectionState.Connected) {
                    ensureAncsSession(state.sessionId)
                } else if (idRegistry.isSessionActive) {
                    endAncsSession()
                }

                val phoneName = getString(R.string.default_phone_name)
                val text = when (shown) {
                    is ConnectionState.Idle -> getString(R.string.service_status_idle)
                    is ConnectionState.WaitingForIphone -> getString(R.string.service_status_waiting)
                    is ConnectionState.Connecting -> getString(R.string.status_connecting_to, shown.deviceName ?: phoneName)
                    is ConnectionState.Bonding -> getString(R.string.status_pairing_with, shown.deviceName ?: phoneName)
                    is ConnectionState.LinkedWithoutAncs -> getString(R.string.service_status_linked_no_ancs)
                    is ConnectionState.Connected -> getString(R.string.service_status_connected_to, shown.deviceName ?: phoneName)
                    is ConnectionState.Disconnected -> getString(R.string.service_status_disconnected)
                    is ConnectionState.RepairNeeded -> getString(R.string.service_status_repair)
                    is ConnectionState.Error -> getString(R.string.service_status_error, shown.message)
                }
                notificationManager.notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(text))
                requestSurfaceUpdate()
            }
        }

        // Start the attribute request processor
        startRequestProcessor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RECONNECT, null -> startLinkLayer()
            ACTION_START_PAIRING -> startPairingWindow()
            ACTION_DISCONNECT -> {
                endPairingWindow()
                advertiser.stop()
                connectionManager.disconnect()
            }
            ACTION_PERFORM_NOTIFICATION_ACTION -> {
                val uid = intent.getLongExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, -1)
                val actionId = intent.getIntExtra(EXTRA_ACTION_ID, -1)
                // 0 = sent by the call UI, which is torn down when a session ends
                val sessionId = intent.getLongExtra(NotificationActionReceiver.EXTRA_SESSION_ID, 0L)
                val androidId = intent.getIntExtra(NotificationActionReceiver.EXTRA_ANDROID_ID, -1)
                if (uid != -1L && actionId != -1) {
                    performNotificationAction(uid, actionId, sessionId, androidId)
                }
            }
            ACTION_MEDIA_COMMAND -> {
                val command = RemoteCommand.fromId(intent.getIntExtra(EXTRA_MEDIA_COMMAND, -1))
                if (command != null) scope.launch { connectionManager.sendMediaCommand(command) }
            }
            ACTION_MUSIC_DEMAND -> connectionManager.setMusicDemanded(intent.getBooleanExtra(EXTRA_MUSIC_DEMANDED, false))
            ACTION_CLEAR_ALL -> {
                Log.i(TAG, "Clearing all notifications (${activeNotificationIds.size} tracked)")
                activeNotificationIds.forEach { id ->
                    notificationManager.cancel(id)
                }
                activeNotificationIds.clear()
                notificationManager.cancel(NOTIFICATION_ID_CALL)
                requestSurfaceUpdate()
            }
            ACTION_STOP -> {
                endPairingWindow()
                advertiser.stop()
                inboundMonitor.close()
                connectionManager.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        listOf(
            connectionManager.bondStateReceiver, adapterStateReceiver, keyMissingReceiver,
            encryptionChangeReceiver, timeChangeReceiver
        ).forEach {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) { }
        }
        advertiser.stop()
        inboundMonitor.close()
        connectionManager.destroy()
        attributeRequestQueue.close()
        scope.cancel()
    }

    /** Listen for the iPhone and, if one is paired, keep a background reconnection going. */
    private fun startLinkLayer() {
        inboundMonitor.open()
        val phone = pairedPhoneStore.bondedPhone()
        if (phone == null) {
            if (pairedPhoneStore.pairedAddress != null) {
                DiagnosticsLog.log("pairing: stored iPhone is no longer bonded — pairing needed")
                pairedPhoneStore.clear()
                clearHistory()
            }
            if (!pairingWindowOpen) DiagnosticsLog.log("pairing: no paired iPhone; waiting for the user to pair")
            return
        }
        DiagnosticsLog.log("pairing: paired iPhone ${phone.address}, reconnecting")
        updateAdvertising(connectionManager.connectionState.value)
        connectionManager.backgroundReconnectEnabled = pairedPhoneStore.reconnectStrategy.useBackgroundRequest
        connectionManager.reconnect(phone)
    }

    private fun startPairingWindow() {
        // Drop any current/background connection: the user is pairing (again)
        connectionManager.disconnect()
        pairedPhoneStore.clear()
        inboundMonitor.open()
        pairingWindowJob?.cancel()
        pairingWindowJob = scope.launch {
            delay(PAIRING_WINDOW_MS)
            DiagnosticsLog.log("pairing: window closed without a paired iPhone")
            pairingWindowJob = null
            updateAdvertising(connectionManager.connectionState.value)
            if (connectionManager.connectionState.value is ConnectionState.Idle) {
                _sharedConnectionState.value = ConnectionState.Error(getString(R.string.pair_status_timeout))
            }
        }
        DiagnosticsLog.log("pairing: window open (${PAIRING_WINDOW_MS / 1000} s)")
        DiagnosticsLog.log("pairing: bonded before pairing: ${pairedPhoneStore.describeBondedDevices()}")
        _sharedConnectionState.value = ConnectionState.WaitingForIphone(advertiser.advertisedName)
        advertiser.start(WatchAdvertiser.Mode.PAIRING)
    }

    private fun endPairingWindow() {
        pairingWindowJob?.cancel()
        pairingWindowJob = null
    }

    /**
     * Advertise only while it helps: fast during pairing, slow while a paired iPhone is
     * disconnected, off while connected (saves battery) or when pairing must be redone.
     */
    private fun updateAdvertising(state: ConnectionState) {
        val paired = pairedPhoneStore.pairedAddress != null
        val waitingForPhone = state is ConnectionState.Disconnected ||
            state is ConnectionState.Error || state is ConnectionState.Idle
        when {
            state is ConnectionState.Connected || state is ConnectionState.RepairNeeded -> advertiser.stop()
            pairingWindowOpen -> advertiser.start(WatchAdvertiser.Mode.PAIRING)
            paired && waitingForPhone && pairedPhoneStore.reconnectStrategy.useAdvertising ->
                advertiser.start(WatchAdvertiser.Mode.RECONNECT)
            // Connecting/Bonding (link already up), not paired, or advertising disabled
            else -> advertiser.stop()
        }
    }

    private fun onInboundLink(device: BluetoothDevice) {
        val decision = PairingPolicy.onInboundLink(
            address = device.address,
            isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
            pairedAddress = pairedPhoneStore.pairedAddress,
            pairingWindowOpen = pairingWindowOpen
        )
        DiagnosticsLog.log("inbound: ${device.address} → $decision")
        if (decision == PairingPolicy.InboundDecision.ATTACH) {
            connectionManager.attachInbound(device)
        }
        // PROBE: a bonded device that doesn't match. The background autoConnect request to
        // the paired identity address picks up the iPhone's link by itself, so don't replace it.
    }

    private fun handleNotificationSourceEvent(data: ByteArray) {
        val event = NotificationSourceParser.parse(data) ?: run {
            Log.w(TAG, "Failed to parse NS event")
            return
        }

        Log.d(TAG, "NS event: id=${event.eventId} cat=${event.categoryId} uid=${event.notificationUid}")

        when {
            event.isAdded || event.isModified -> {
                // Skip the backlog (PreExisting) at the NS level — don't even fetch
                // attributes. This avoids GATT writes that trigger system haptics.
                if (AncsEventFilter.decide(event) == AncsEventFilter.Decision.SKIP_PRE_EXISTING) {
                    countSkippedBacklog()
                    return
                }
                DiagnosticsLog.log("ancs: NS added/modified uid=${event.notificationUid} cat=${event.categoryId} flags=0x${event.eventFlags.toString(16)}")

                // MODIFIED on active call = call was answered on iPhone.
                // Transition the call overlay to "Active on iPhone" state.
                if (event.isModified && event.isIncomingCall && activeCallUid == event.notificationUid) {
                    Log.i(TAG, "Call MODIFIED (answered on iPhone) uid=${event.notificationUid}")
                    vibrator.cancel()
                    // Cancel the CallStyle notification — no longer ringing
                    notificationManager.cancel(NOTIFICATION_ID_CALL)
                    // Tell the activity to transition to "active call" UI
                    sendBroadcast(Intent(IncomingCallActivity.ACTION_CALL_ANSWERED).apply {
                        setPackage(packageName)
                    })
                    return
                }

                if (event.isIncomingCall && NotificationPolicy.shouldRingForCall(settings, minuteOfDay())) {
                    // IMMEDIATELY show call screen — don't wait for attribute fetch.
                    // The NS payload already tells us it's a call. We'll update
                    // the caller name later if attributes arrive in time.
                    Log.i(TAG, "Incoming call NS event (cat=1) uid=${event.notificationUid} — showing call screen NOW")
                    showIncomingCall(AncsNotification(
                        uid = event.notificationUid,
                        categoryId = event.categoryId,
                        eventFlags = event.eventFlags,
                        appIdentifier = "com.apple.mobilephone",
                        title = getString(R.string.call_incoming),
                        subtitle = null,
                        message = "",
                        date = null,
                        appDisplayName = getString(R.string.call_default_app),
                        positiveActionLabel = getString(R.string.call_answer),
                        negativeActionLabel = getString(R.string.call_decline)
                    ))
                }

                // Build list of attributes to request
                val attrs = mutableListOf(
                    AncsConstants.ATTR_APP_IDENTIFIER,
                    AncsConstants.ATTR_TITLE,
                    AncsConstants.ATTR_SUBTITLE,
                    AncsConstants.ATTR_MESSAGE,
                    AncsConstants.ATTR_DATE
                )
                if (event.hasPositiveAction) attrs.add(AncsConstants.ATTR_POSITIVE_ACTION_LABEL)
                if (event.hasNegativeAction) attrs.add(AncsConstants.ATTR_NEGATIVE_ACTION_LABEL)

                // Enqueue attribute fetch (for all notifications including calls)
                val sessionId = idRegistry.sessionId
                attributeRequestsPending.incrementAndGet()
                scope.launch {
                    attributeRequestQueue.send(AttributeRequest(sessionId, event, attrs))
                }
            }
            event.isRemoved -> {
                // Cancel the Android notification and remove from tracking
                // Only notifications posted in this session: an old one with the same
                // (renumbered) UID must not be cancelled
                val removedSession = idRegistry.sessionId
                idRegistry.remove(event.notificationUid)?.let { removedId ->
                    notificationManager.cancel(removedId)
                    activeNotificationIds.remove(removedId)
                    scope.launch(Dispatchers.IO) { history.removedOnIphone(removedId, removedSession) }
                    requestSurfaceUpdate()
                }

                // If this was an incoming call, dismiss everything
                if (event.isIncomingCall || activeCallUid == event.notificationUid) {
                    Log.i(TAG, "Call ended (REMOVE) uid=${event.notificationUid}")
                    activeCallUid = -1
                    vibrator.cancel()
                    notificationManager.cancel(NOTIFICATION_ID_CALL)
                    AncsConnectionService.endActiveCall()
                    sendBroadcast(Intent(IncomingCallActivity.ACTION_CALL_ENDED).apply {
                        setPackage(packageName)
                    })
                }
            }
        }
    }

    private fun handleDataSourceEvent(data: ByteArray) {
        val notification = dataSourceAssembler.onDataReceived(data)
        if (notification != null) {
            Log.i(TAG, "Notification complete: uid=${notification.uid}")
            pendingAttributeResponse?.complete(Unit)
            if (!firstNotificationLogged) {
                firstNotificationLogged = true
                DiagnosticsLog.log(
                    "FIRST NOTIFICATION RECEIVED: " + DiagnosticsLog.notificationPreview(
                        notification.appIdentifier, notification.title, notification.message
                    )
                )
            }
            scope.launch {
                postNotification(notification)
            }
        }
    }

    private fun startRequestProcessor() {
        requestProcessorJob = scope.launch {
            for (request in attributeRequestQueue) {
                try {
                    processAttributeRequest(request)
                } finally {
                    attributeRequestsPending.updateAndGet { (it - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    private suspend fun processAttributeRequest(request: AttributeRequest) {
        val event = request.event
        if (!idRegistry.isCurrent(request.sessionId)) {
            Log.d(TAG, "Dropping attribute request for uid=${event.notificationUid} from an ended session")
            return
        }

        // Prepare the assembler to expect this response
        dataSourceAssembler.expectNotificationAttributes(
            uid = event.notificationUid,
            requestedAttributes = request.requestedAttributes,
            category = event.categoryId,
            flags = event.eventFlags
        )

        // Write the Control Point request
        val cpData = ControlPointWriter.buildGetNotificationAttributes(
            uid = event.notificationUid,
            hasPositiveAction = event.hasPositiveAction,
            hasNegativeAction = event.hasNegativeAction
        )

        val response = CompletableDeferred<Unit>()
        pendingAttributeResponse = response
        try {
            if (!connectionManager.writeControlPoint(cpData)) {
                Log.e(TAG, "Failed to write Control Point for uid=${event.notificationUid}")
                dataSourceAssembler.reset()
                return
            }
            // The response arrives via dataSourceEvents -> handleDataSourceEvent. Wait for it
            // so the next request can't reset the assembler mid-response.
            if (withTimeoutOrNull(ATTRIBUTE_RESPONSE_TIMEOUT_MS) { response.await() } == null) {
                Log.w(TAG, "Timed out waiting for attributes of uid=${event.notificationUid}")
                dataSourceAssembler.reset()
            }
        } finally {
            pendingAttributeResponse = null
        }
    }

    private suspend fun postNotification(notification: AncsNotification) {
        val isPreExisting = notification.eventFlags and AncsConstants.EVENT_FLAG_PRE_EXISTING != 0

        Log.d(TAG, "postNotification: uid=${notification.uid} cat=${notification.categoryId} " +
            "flags=0x${notification.eventFlags.toString(16)} app=${notification.appIdentifier} " +
            "title=${notification.title} preExisting=$isPreExisting")

        // Resolve app display name if not cached
        val appName = resolveAppName(notification.appIdentifier)
        val updatedNotification = notification.copy(appDisplayName = appName)

        // Detect incoming calls by category OR by message content
        // WhatsApp sends calls as cat=4 (Social) with "☎" or "Incoming" in message
        // Regular phone may send as cat=1 (IncomingCall) or cat=2 with "Incoming" content
        val callApps = setOf(
            "net.whatsapp.WhatsApp", "net.whatsapp.WhatsAppSMB",
            "com.apple.mobilephone", "com.apple.facetime"
        )
        val messageAndTitle = "${notification.message} ${notification.title}"
        val hasCallIndicator = messageAndTitle.contains("Incoming Call", ignoreCase = true) ||
            messageAndTitle.contains("Incoming Voice", ignoreCase = true) ||
            messageAndTitle.contains("Incoming Video", ignoreCase = true) ||
            messageAndTitle.contains("Incoming Audio", ignoreCase = true) ||
            messageAndTitle.contains("☎") ||
            messageAndTitle.contains("Audio Call", ignoreCase = true) ||
            messageAndTitle.contains("Video Call", ignoreCase = true) ||
            messageAndTitle.contains("Voice Call", ignoreCase = true) ||
            messageAndTitle.contains("is calling", ignoreCase = true) ||
            messageAndTitle.contains("Ringing", ignoreCase = true)
        val isIncomingCall = notification.categoryId == AncsConstants.CATEGORY_INCOMING_CALL ||
            (notification.positiveActionLabel != null && hasCallIndicator) ||
            (notification.appIdentifier in callApps && hasCallIndicator)

        // Quiet hours without the calls exception: the call is posted as a silent notification
        if (isIncomingCall && NotificationPolicy.shouldRingForCall(settings, minuteOfDay())) {
            Log.i(TAG, "Showing incoming call screen for ${notification.title}")
            showIncomingCall(updatedNotification)
            return
        }

        // Skip pre-existing notifications — they're already on the phone.
        if (isPreExisting) {
            Log.d(TAG, "Skipping pre-existing notification uid=${notification.uid}")
            return
        }

        // Grace period and pre-existing checks are now done at the NS event level
        // (handleNotificationSourceEvent) so we never even fetch attributes for those.

        // Route alarm/timer apps to the schedule channel regardless of ANCS category
        val alarmApps = setOf("com.apple.mobiletimer", "com.apple.reminders", "com.apple.mobilecal")
        val channelId = if (notification.appIdentifier in alarmApps) {
            AncsApplication.CHANNEL_SCHEDULE
        } else {
            AncsApplication.channelForCategory(notification.categoryId)
        }
        val iconResId = AppIconMapper.getIconResId(notification.appIdentifier, notification.categoryId)
        Log.d(TAG, "Icon mapping: app='${notification.appIdentifier}' cat=${notification.categoryId} -> resId=$iconResId (default=${R.drawable.ic_notification_default})")
        if (!idRegistry.isSessionActive) {
            Log.w(TAG, "Dropping uid=${notification.uid}: no ANCS session")
            return
        }
        // Fresh id per (session, UID): a renumbered UID never replaces an old notification
        // silently. MODIFIED events reuse the id and update in place (setOnlyAlertOnce).
        val decision = NotificationPolicy.decide(settings, notification.appIdentifier, minuteOfDay())
        val displayAppName = appName ?: notification.appIdentifier
        if (!decision.show) {
            // App disabled in Settings: nothing posted or stored, but it stays listed in Settings › Apps
            scope.launch(Dispatchers.IO) {
                history.rememberApp(notification.appIdentifier, displayAppName, notification.categoryId)
            }
            return
        }

        // Already posted in this session = a MODIFIED update of it: never re-classified
        val isUpdate = idRegistry.existingId(notification.uid) != null
        val restored = if (isUpdate) null else classifyRestored(notification)

        val notifId = idRegistry.idFor(notification.uid)
        val sessionId = idRegistry.sessionId

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Layout: contentTitle = sender, contentText = message preview, subText = app name
        val realSender = notification.title.ifEmpty { notification.appDisplayName ?: notification.appIdentifier }
        val realPreview = notification.message.ifEmpty { notification.subtitle ?: "" }
        // "Hide message text" for this app: only the app name and a generic line
        val senderName = if (decision.hideContent) displayAppName else realSender
        val messagePreview = if (decision.hideContent) getString(R.string.notification_hidden_text, displayAppName) else realPreview
        val notifTime = parseAncsDate(notification.date)

        val entry = HistoryEntry(
            androidId = notifId,
            sessionId = sessionId,
            uid = notification.uid,
            appId = notification.appIdentifier,
            appName = displayAppName,
            categoryId = notification.categoryId,
            // Hidden content is never written to disk
            title = if (decision.hideContent) "" else realSender,
            message = if (decision.hideContent) "" else realPreview,
            postedAt = notifTime,
            positiveLabel = notification.positiveActionLabel,
            negativeLabel = notification.negativeActionLabel,
            contentHidden = decision.hideContent
        )

        // Restored by the iPhone after a restart: no sound, vibration or pop-up. It goes to
        // Recent only; with history off it is posted silently instead, so it's never lost.
        val restoredSilently = restored?.verdict == RestoredNotificationRule.Verdict.RESTORED
        if (restoredSilently && settings.historyEnabled) {
            scope.launch(Dispatchers.IO) { history.record(entry, store = true) }
            requestSurfaceUpdate()
            return
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconResId)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setSubText(appName ?: notification.appIdentifier)
            .setTicker("$senderName: $messagePreview")
            .setWhen(notifTime)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidCategory(notification.categoryId))
            // Each notification gets a unique group key to prevent Android's auto-grouping.
            // Auto-grouping adds SILENT flag via GROUP_ALERT_SUMMARY which kills
            // heads-up overlays, vibration, and sound on ALL child notifications.
            .setGroup("ancs_${notification.uid}")
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            // New id = alerts; MODIFIED updates of the same notification don't buzz again
            .setOnlyAlertOnce(true)
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)

        // MessagingStyle — Samsung One UI Watch renders this with message body
        // in the heads-up overlay, matching native Android-paired notification style.
        // BigTextStyle only shows body in expanded view, not the overlay.
        if (messagePreview.isNotEmpty()) {
            val person = androidx.core.app.Person.Builder()
                .setName(senderName)
                .build()
            builder.setStyle(
                NotificationCompat.MessagingStyle(person)
                    .addMessage(messagePreview, notifTime, person)
            )
        } else if (notification.message.isNotEmpty() && !decision.hideContent) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notification.message)
                    .setBigContentTitle(senderName)
                    .setSummaryText(appName)
            )
        }

        // Add action buttons if available
        if (notification.positiveActionLabel != null) {
            val positiveIntent = PendingIntent.getBroadcast(
                this, notifId,
                Intent(NotificationActionReceiver.ACTION_POSITIVE).apply {
                    setPackage(packageName)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notification.uid)
                    putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, sessionId)
                    putExtra(NotificationActionReceiver.EXTRA_ANDROID_ID, notifId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_check, notification.positiveActionLabel, positiveIntent)
        }

        if (notification.negativeActionLabel != null) {
            val negativeIntent = PendingIntent.getBroadcast(
                this, notifId + 500_000,
                Intent(NotificationActionReceiver.ACTION_NEGATIVE).apply {
                    setPackage(packageName)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notification.uid)
                    putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, sessionId)
                    putExtra(NotificationActionReceiver.EXTRA_ANDROID_ID, notifId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_close, notification.negativeActionLabel, negativeIntent)
        }

        // Never call setNotificationSilent() for normal notifications — it suppresses the
        // heads-up overlay on Wear OS. Only a muted app or quiet hours ask for exactly that.
        if (decision.alert == NotificationPolicy.Alert.SILENT || restoredSilently) builder.setSilent(true)

        // Lock screen: a public version with only the app name
        if (decision.privateOnLockScreen) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(iconResId)
                    .setContentTitle(displayAppName)
                    .setContentText(getString(R.string.notification_hidden_text, displayAppName))
                    .build()
            )
        }

        // Auto-cleanup: evict oldest notifications if approaching the system cap
        evictOldestIfNeeded()

        notificationManager.notify(notifId, builder.build())
        activeNotificationIds.addLast(notifId)
        requestSurfaceUpdate()
        scope.launch(Dispatchers.IO) { history.record(entry, store = settings.historyEnabled) }
    }

    /**
     * Was this notification re-sent by the iPhone after a restart (see
     * RestoredNotificationRule)? Also remembers its fingerprint. In doubt: NEW.
     */
    private fun classifyRestored(notification: AncsNotification): RestoredNotificationRule.Result {
        val now = System.currentTimeMillis()
        val fingerprint = SeenNotifications.fingerprint(
            notification.appIdentifier, notification.title, notification.message, notification.date
        )
        val seenBefore = fingerprint != null && seenNotifications.contains(fingerprint, now)
        val zoneAutomatic = try {
            android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.AUTO_TIME_ZONE, 1) == 1
        } catch (e: Exception) {
            false
        }
        val result = RestoredNotificationRule.classify(
            dateText = notification.date,
            zone = java.time.ZoneId.systemDefault(),
            sessionStartMs = sessionStartedAtMs,
            nowMs = now,
            seenBefore = seenBefore,
            sessionClockDoubt = sessionClockDoubt,
            watchZoneAutomatic = zoneAutomatic
        )
        if (result.clockDoubt && !sessionClockDoubt) {
            sessionClockDoubt = true
            DiagnosticsLog.log("ancs: clock/zone doubt (${result.reason}): restored-notification check off for this session")
        }
        fingerprint?.let { scope.launch(Dispatchers.IO) { seenNotifications.add(it, now) } }
        DiagnosticsLog.log(
            "ancs: uid=${notification.uid} ${result.verdict.name.lowercase()} (${result.reason})" +
                if (result.verdict == RestoredNotificationRule.Verdict.RESTORED) " → silent, Recent only" else ""
        )
        return result
    }

    /** Header line in the diagnostics file, so each run (and app version) is easy to find. */
    private fun logServiceStart() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).let { "${it.versionName} (${it.longVersionCode})" }
        } catch (e: Exception) {
            "?"
        }
        DiagnosticsLog.log(
            "=== service start: TandemWear $version, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                "SDK ${android.os.Build.VERSION.SDK_INT}, zone ${java.util.TimeZone.getDefault().id} ==="
        )
    }

    /**
     * Tile and complication refresh, coalesced: a burst of notifications or state changes
     * becomes one update, after the history write has landed.
     */
    private fun requestSurfaceUpdate() {
        surfaceUpdateJob?.cancel()
        surfaceUpdateJob = scope.launch {
            delay(SURFACE_UPDATE_DELAY_MS)
            try {
                SurfaceState.requestUpdate(this@AncsService)
            } catch (e: Exception) {
                Log.w(TAG, "Tile/complication update request failed", e)
            }
        }
    }

    /** A (new) iPhone was paired: history from another iPhone or an older bond is dropped. */
    private fun onPhonePaired(identity: String) {
        val prefs = getSharedPreferences(PREFS_HISTORY, MODE_PRIVATE)
        if (prefs.getString(KEY_HISTORY_OWNER, null) != identity) {
            clearHistory()
            prefs.edit().putString(KEY_HISTORY_OWNER, identity).apply()
        }
    }

    /** Unpaired: decision of 21-10, the history is cleared. */
    private fun clearHistory() {
        DiagnosticsLog.log("history: cleared (iPhone unpaired or changed)")
        scope.launch(Dispatchers.IO) {
            history.clear()
            seenNotifications.clear()
        }
    }

    /**
     * Evict the oldest notifications to stay under the system limit.
     * Keeps MAX_ACTIVE_NOTIFICATIONS slots, leaving room for service + call notifications.
     */
    private fun evictOldestIfNeeded() {
        while (activeNotificationIds.size >= MAX_ACTIVE_NOTIFICATIONS) {
            val oldId = activeNotificationIds.removeFirst()
            notificationManager.cancel(oldId)
            Log.d(TAG, "Auto-evicted oldest notification id=$oldId (${activeNotificationIds.size} active)")
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun showIncomingCall(notification: AncsNotification) {
        val placeholderTitle = getString(R.string.call_incoming)
        val callerName = notification.title.ifEmpty { placeholderTitle }
        val appName = notification.appDisplayName ?: getString(R.string.call_default_app)

        // Track active call UID to avoid duplicate showIncomingCall calls
        if (activeCallUid == notification.uid) {
            // Already showing — just update caller name if it changed
            if (callerName != placeholderTitle) {
                AncsConnectionService.activeConnection?.updateCallerName(callerName)
                // Also update the IncomingCallActivity via broadcast
                sendBroadcast(Intent(IncomingCallActivity.ACTION_CALLER_NAME_UPDATED).apply {
                    setPackage(packageName)
                    putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
                })
                Log.i(TAG, "Updated caller name to '$callerName' for uid=${notification.uid}")
            }
            return
        }
        activeCallUid = notification.uid

        // 1. Report to Telecom framework — self-managed connection allows
        //    our foreground service to launch activities while ringing
        AncsConnectionService.reportIncomingCall(
            this, notification.uid, callerName, appName
        )

        // 2. Directly launch call activity — the Telecom self-managed connection
        //    grants us the background activity start exception
        val callIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallActivity.EXTRA_NOTIFICATION_UID, notification.uid)
            putExtra(IncomingCallActivity.EXTRA_APP_NAME, appName)
        }
        try {
            startActivity(callIntent)
            Log.i(TAG, "Started IncomingCallActivity directly for '$callerName'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start IncomingCallActivity: ${e.message}")
        }

        // 3. Post CallStyle notification as fallback
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answerPendingIntent = PendingIntent.getActivity(
            this, 1, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePendingIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(NotificationActionReceiver.ACTION_NEGATIVE).apply {
                setPackage(packageName)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notification.uid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val caller = androidx.core.app.Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()
        val callNotification = NotificationCompat.Builder(this, AncsApplication.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_phone)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller, declinePendingIntent, answerPendingIntent
                )
            )
            .build()
        notificationManager.notify(NOTIFICATION_ID_CALL, callNotification)
        Log.i(TAG, "Posted CallStyle notification for '$callerName'")
    }


    private fun performNotificationAction(uid: Long, actionId: Int, sessionId: Long, androidId: Int) {
        // Cancel vibration (in case this is a call answer/decline)
        vibrator.cancel()

        val stale = sessionId != NotificationIdRegistry.NO_SESSION && !idRegistry.isCurrent(sessionId)
        if (stale) {
            // The UID now names a different notification on the iPhone: never send it
            DiagnosticsLog.log("ancs: action on a notification from an ended session — dismissed locally only")
        } else {
            val data = ControlPointWriter.buildPerformNotificationAction(uid, actionId)
            scope.launch {
                if (!connectionManager.writeControlPoint(data)) {
                    Log.e(TAG, "Failed to perform action $actionId on uid=$uid")
                }
            }
        }

        // Cancel the Android notification and remove from tracking
        val notifId = androidId.takeIf { it != -1 }
            ?: if (stale) null else idRegistry.existingId(uid)
        if (!stale) idRegistry.remove(uid)
        notifId?.let {
            notificationManager.cancel(it)
            activeNotificationIds.remove(it)
        }
    }

    /** Start tracking a new ANCS session if [sessionId] differs from the current one. */
    private fun ensureAncsSession(sessionId: Long) {
        if (sessionId == NotificationIdRegistry.NO_SESSION || idRegistry.isCurrent(sessionId)) return
        if (idRegistry.isSessionActive) endAncsSession()
        idRegistry.startSession(sessionId)
        sessionStartedAtMs = System.currentTimeMillis()
        sessionClockDoubt = false
        DiagnosticsLog.log("ancs: new session — UIDs restart, notifications from earlier sessions keep their own ids")
    }

    /**
     * The ANCS session ended: every UID-bound piece of state is now meaningless (Apple spec).
     * Notifications already on the watch stay (their actions become local-only).
     */
    private fun endAncsSession() {
        flushBacklogSummary()
        val mapped = idRegistry.mappedCount
        idRegistry.endSession()
        _sharedMusic.value = MusicSession.NONE
        dataSourceAssembler.reset()
        pendingAttributeResponse?.complete(Unit)
        var dropped = 0
        while (attributeRequestQueue.tryReceive().isSuccess) dropped++
        attributeRequestsPending.updateAndGet { (it - dropped).coerceAtLeast(0) }
        if (activeCallUid != -1L) {
            activeCallUid = -1
            vibrator.cancel()
            notificationManager.cancel(NOTIFICATION_ID_CALL)
            AncsConnectionService.endActiveCall()
            sendBroadcast(Intent(IncomingCallActivity.ACTION_CALL_ENDED).setPackage(packageName))
        }
        DiagnosticsLog.log("ancs: session ended — cleared $mapped UID mappings, $dropped pending requests")
    }

    private fun countSkippedBacklog() {
        skippedBacklogCount++
        backlogSummaryJob?.cancel()
        backlogSummaryJob = scope.launch {
            delay(BACKLOG_SUMMARY_DELAY_MS)
            flushBacklogSummary()
        }
    }

    private fun flushBacklogSummary() {
        backlogSummaryJob?.cancel()
        backlogSummaryJob = null
        if (skippedBacklogCount > 0) {
            DiagnosticsLog.log("ancs: $skippedBacklogCount old notifications skipped (PreExisting)")
            skippedBacklogCount = 0
        }
    }

    private fun resolveAppName(appIdentifier: String): String? {
        if (appIdentifier.isEmpty()) return null
        appNameCache[appIdentifier]?.let { return it }

        // For now, derive a readable name from the bundle ID
        // A full implementation would use GetAppAttributes, but that requires
        // another CP/DS round trip. We use a simple mapping instead.
        val name = KnownApps.displayName(appIdentifier)
        appNameCache[appIdentifier] = name
        return name
    }

    private fun buildNotificationText(notification: AncsNotification): String {
        return buildString {
            if (notification.title.isNotEmpty()) {
                append(notification.title)
            }
            if (!notification.subtitle.isNullOrEmpty()) {
                if (isNotEmpty()) append(": ")
                append(notification.subtitle)
            }
            if (notification.message.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(notification.message)
            }
        }
    }

    /** Local minute of the day, for quiet hours. */
    private fun minuteOfDay(): Int {
        val now = java.time.LocalTime.now()
        return now.hour * 60 + now.minute
    }

    private fun parseAncsDate(date: String?): Long {
        if (date == null) return System.currentTimeMillis()
        return try {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US)
            sdf.parse(date)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun androidCategory(categoryId: Int): String = when (categoryId) {
        AncsConstants.CATEGORY_INCOMING_CALL -> Notification.CATEGORY_CALL
        AncsConstants.CATEGORY_MISSED_CALL -> Notification.CATEGORY_MISSED_CALL
        AncsConstants.CATEGORY_VOICEMAIL -> Notification.CATEGORY_VOICEMAIL
        AncsConstants.CATEGORY_SOCIAL -> Notification.CATEGORY_SOCIAL
        AncsConstants.CATEGORY_SCHEDULE -> Notification.CATEGORY_EVENT
        AncsConstants.CATEGORY_EMAIL -> Notification.CATEGORY_EMAIL
        else -> Notification.CATEGORY_MESSAGE
    }

    private fun buildServiceNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AncsApplication.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_default)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
