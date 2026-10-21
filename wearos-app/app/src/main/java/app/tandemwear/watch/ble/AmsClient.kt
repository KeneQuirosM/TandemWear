package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.SystemClock
import app.tandemwear.watch.ams.AmsPolicy
import app.tandemwear.watch.ams.AmsProtocol
import app.tandemwear.watch.ams.Entity
import app.tandemwear.watch.ams.RemoteCommand
import app.tandemwear.watch.ble.GattOperationQueue.OpKey
import app.tandemwear.watch.diag.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** What AMS reports to the service (on the BLE thread or a binder thread). */
sealed interface AmsPacket {
    val sessionId: Long

    /** Raw Entity Update notification. */
    class EntityUpdate(override val sessionId: Long, val data: ByteArray) : AmsPacket
    /** Raw Remote Command notification (supported commands). */
    class SupportedCommands(override val sessionId: Long, val data: ByteArray) : AmsPacket
    /** Full value of a truncated attribute (Entity Attribute read). */
    class FullValue(override val sessionId: Long, val entity: Entity, val attribute: Int, val value: String) : AmsPacket
    /** Subscription status: [available] = the iPhone publishes AMS on this link. */
    class Status(override val sessionId: Long, val available: Boolean, val subscribed: Boolean) : AmsPacket
}

/**
 * Apple Media Service client on the same GATT link as ANCS (Phase 3).
 *
 * Isolation from ANCS is the point of this class:
 * - it starts only after SESSION ACTIVE, plus [AmsPolicy.START_DELAY_MS];
 * - every step waits until the GATT queue is idle and no ANCS attribute request is pending
 *   ([AmsPolicy.canProceed]), and uses a short timeout ([AmsPolicy.OP_TIMEOUT_MS]);
 * - a failure only logs and turns AMS off for the session: it never changes the connection
 *   state, never disconnects and never counts as a key-loss signal.
 *
 * All methods run on the BLE dispatcher ([scope], single thread) except [onNotification].
 */
@SuppressLint("MissingPermission")
internal class AmsClient(
    private val scope: CoroutineScope,
    private val operations: GattOperationQueue,
    /** ANCS attribute requests queued or waiting for their Data Source response. */
    private val ancsRequestsPending: () -> Int,
    /** elapsedRealtime of the last ANCS activity (NS/DS event, Control Point write). */
    private val lastAncsActivityMs: () -> Long,
    private val emit: (AmsPacket) -> Unit
) {
    private var gatt: BluetoothGatt? = null
    @Volatile private var sessionId = 0L
    private var sessionActive = false
    private var job: Job? = null
    private var subscribed = false
    private var failed = false
    private val fetching = mutableSetOf<Pair<Entity, Int>>()
    private var pendingAttributeRead: Pair<Entity, Int>? = null

    /** User setting "Música". */
    var musicEnabled = true
        private set

    /** ON_DEMAND mode: the player screen or the Tile wants music data. */
    private var demanded = false

    private val amsAvailable: Boolean get() = gatt?.getService(AmsProtocol.SERVICE_UUID) != null

    fun onAncsSessionActive(g: BluetoothGatt, session: Long) {
        gatt = g
        sessionId = session
        sessionActive = true
        subscribed = false
        failed = false
        fetching.clear()
        if (!amsAvailable) {
            DiagnosticsLog.log("ams: not published on this link (${g.services.size} services) — music unavailable")
            emit(AmsPacket.Status(session, available = false, subscribed = false))
            return
        }
        maybeStart("session active")
    }

    fun onSessionEnded() {
        job?.cancel()
        job = null
        if (sessionActive && subscribed) DiagnosticsLog.log("ams: session ended")
        sessionActive = false
        subscribed = false
        gatt = null
        pendingAttributeRead = null
        fetching.clear()
    }

    fun setMusicEnabled(enabled: Boolean) {
        if (musicEnabled == enabled) return
        musicEnabled = enabled
        DiagnosticsLog.log("ams: music ${if (enabled) "enabled" else "disabled"}")
        if (enabled) maybeStart("music enabled") else unsubscribe()
    }

    fun setDemanded(wanted: Boolean) {
        if (demanded == wanted) return
        demanded = wanted
        if (wanted) maybeStart("player opened")
    }

    private fun maybeStart(reason: String) {
        val g = gatt ?: return
        if (subscribed || failed || job?.isActive == true) return
        if (!AmsPolicy.shouldSubscribe(AmsPolicy.START_MODE, musicEnabled, sessionActive, amsAvailable, demanded)) return
        val session = sessionId
        job = scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            delay(AmsPolicy.START_DELAY_MS)
            DiagnosticsLog.log("ams: starting ($reason), ${AmsPolicy.subscriptionPlan().size} steps")
            for (step in AmsPolicy.subscriptionPlan()) {
                if (gatt !== g || sessionId != session) return@launch
                if (!runStep(g, step)) {
                    failed = true
                    DiagnosticsLog.log("ams: ${step.label} FAILED — AMS off for this session, ANCS unaffected")
                    emit(AmsPacket.Status(session, available = true, subscribed = false))
                    return@launch
                }
            }
            subscribed = true
            DiagnosticsLog.log("ams: subscribed ${SystemClock.elapsedRealtime() - t0} ms after session active")
            emit(AmsPacket.Status(session, available = true, subscribed = true))
        }
    }

    private fun unsubscribe() {
        job?.cancel()
        val g = gatt ?: return
        if (!subscribed) return
        subscribed = false
        val session = sessionId
        emit(AmsPacket.Status(session, available = true, subscribed = false))
        job = scope.launch {
            for (uuid in listOf(AmsProtocol.ENTITY_UPDATE_UUID, AmsProtocol.REMOTE_COMMAND_UUID)) {
                if (gatt !== g) return@launch
                val char = characteristic(g, uuid) ?: continue
                g.setCharacteristicNotification(char, false)
                runOp(g, "stop notifications ${shortName(uuid)}", OpKey.WriteDescriptor(uuid)) {
                    val cccd = char.getDescriptor(AncsConstants.CCCD_UUID) ?: return@runOp false
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                }
            }
        }
    }

    /** Remote Command write (user action). Returns false if AMS isn't ready or the write failed. */
    suspend fun sendCommand(command: RemoteCommand): Boolean {
        val g = gatt
        if (g == null || !subscribed) {
            DiagnosticsLog.log("ams: command ${command.name} ignored (not subscribed)")
            return false
        }
        val char = characteristic(g, AmsProtocol.REMOTE_COMMAND_UUID) ?: return false
        return runOp(g, "command ${command.name}", OpKey.WriteCharacteristic(char.uuid), maxWaitMs = COMMAND_MAX_WAIT_MS) {
            g.writeCharacteristic(char, AmsProtocol.remoteCommand(command), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        }
    }

    /** Binder thread: forward right away (ordering), then look for truncated values on the BLE thread. */
    fun onNotification(uuid: UUID, data: ByteArray) {
        val session = sessionId
        when (uuid) {
            AmsProtocol.ENTITY_UPDATE_UUID -> {
                emit(AmsPacket.EntityUpdate(session, data))
                val update = AmsProtocol.parseEntityUpdate(data) ?: return
                val key = update.entity to update.attribute
                if (update.truncated && key in AmsPolicy.FETCH_FULL) scope.launch { fetchFullValue(key) }
            }
            AmsProtocol.REMOTE_COMMAND_UUID -> emit(AmsPacket.SupportedCommands(session, data))
        }
    }

    /** BLE thread: result of an Entity Attribute read. */
    fun onEntityAttributeRead(value: ByteArray) {
        val key = pendingAttributeRead ?: return
        pendingAttributeRead = null
        emit(AmsPacket.FullValue(sessionId, key.first, key.second, String(value, Charsets.UTF_8)))
    }

    /** BLE thread: a write to an AMS characteristic finished with [status]. */
    fun onWriteStatus(uuid: UUID, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            DiagnosticsLog.log("ams: ${shortName(uuid)} write status=0x${status.toString(16)} (${errorName(status)})")
        }
    }

    private suspend fun fetchFullValue(key: Pair<Entity, Int>) {
        val g = gatt ?: return
        if (!subscribed || !fetching.add(key)) return
        try {
            val char = characteristic(g, AmsProtocol.ENTITY_ATTRIBUTE_UUID) ?: return
            val label = "${key.first.name.lowercase()}/${key.second}"
            val wrote = runOp(g, "request full $label", OpKey.WriteCharacteristic(char.uuid)) {
                g.writeCharacteristic(
                    char, AmsProtocol.entityAttributeRequest(key.first, key.second),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            }
            if (!wrote || gatt !== g) return
            pendingAttributeRead = key
            if (!runOp(g, "read full $label", OpKey.ReadCharacteristic(char.uuid)) { g.readCharacteristic(char) }) {
                pendingAttributeRead = null
            }
        } finally {
            fetching.remove(key)
        }
    }

    private suspend fun runStep(g: BluetoothGatt, step: AmsPolicy.Step): Boolean = when (step) {
        is AmsPolicy.Step.EnableNotifications -> {
            val char = characteristic(g, step.characteristic)
            val cccd = char?.getDescriptor(AncsConstants.CCCD_UUID)
            if (char == null || cccd == null || !g.setCharacteristicNotification(char, true)) {
                DiagnosticsLog.log("ams: ${step.label}: characteristic or CCCD missing")
                false
            } else {
                runOp(g, step.label, OpKey.WriteDescriptor(char.uuid)) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                }
            }
        }
        is AmsPolicy.Step.Write -> {
            val char = characteristic(g, step.characteristic)
            if (char == null) {
                DiagnosticsLog.log("ams: ${step.label}: characteristic missing")
                false
            } else {
                runOp(g, step.label, OpKey.WriteCharacteristic(char.uuid)) {
                    g.writeCharacteristic(char, step.value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                        BluetoothStatusCodes.SUCCESS
                }
            }
        }
    }

    /**
     * Waits until ANCS is idle, then runs one GATT operation with the short AMS timeout.
     * Logs the wait and the duration. False if the wait timed out, the link changed or it failed.
     */
    private suspend fun runOp(
        g: BluetoothGatt,
        label: String,
        key: OpKey,
        maxWaitMs: Long = Long.MAX_VALUE,
        start: () -> Boolean
    ): Boolean {
        val waitStart = SystemClock.elapsedRealtime()
        val idle = withTimeoutOrNull(maxWaitMs) {
            while (!AmsPolicy.canProceed(
                    operations.isBusy, operations.pendingCount, ancsRequestsPending(),
                    SystemClock.elapsedRealtime() - lastAncsActivityMs()
                )
            ) {
                delay(AmsPolicy.IDLE_POLL_MS)
            }
            true
        } ?: false
        val waited = SystemClock.elapsedRealtime() - waitStart
        if (!idle || gatt !== g) {
            DiagnosticsLog.log("ams: $label skipped after waiting $waited ms for ANCS")
            return false
        }
        val opStart = SystemClock.elapsedRealtime()
        val ok = operations.enqueue("ams: $label", key, AmsPolicy.OP_TIMEOUT_MS, start).await()
        DiagnosticsLog.log(
            "ams: $label ${if (ok) "ok" else "failed"} in ${SystemClock.elapsedRealtime() - opStart} ms" +
                if (waited >= AmsPolicy.IDLE_POLL_MS) " (waited $waited ms for ANCS)" else ""
        )
        return ok
    }

    private fun characteristic(g: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? =
        g.getService(AmsProtocol.SERVICE_UUID)?.getCharacteristic(uuid)

    private fun shortName(uuid: UUID) = when (uuid) {
        AmsProtocol.ENTITY_UPDATE_UUID -> "Entity Update"
        AmsProtocol.REMOTE_COMMAND_UUID -> "Remote Command"
        AmsProtocol.ENTITY_ATTRIBUTE_UUID -> "Entity Attribute"
        else -> uuid.toString()
    }

    private fun errorName(status: Int) = when (status) {
        AmsProtocol.ERROR_INVALID_STATE -> "invalid state"
        AmsProtocol.ERROR_INVALID_COMMAND -> "invalid command"
        AmsProtocol.ERROR_ABSENT_ATTRIBUTE -> "absent attribute"
        else -> "GATT error"
    }

    companion object {
        /** A button press waits at most this long for ANCS to be idle. */
        const val COMMAND_MAX_WAIT_MS = 5_000L

        val CHARACTERISTICS: Set<UUID> = setOf(
            AmsProtocol.REMOTE_COMMAND_UUID, AmsProtocol.ENTITY_UPDATE_UUID, AmsProtocol.ENTITY_ATTRIBUTE_UUID
        )
    }
}
