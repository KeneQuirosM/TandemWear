package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.os.SystemClock
import app.tandemwear.watch.ams.AmsPolicy
import app.tandemwear.watch.battery.BatteryProtocol
import app.tandemwear.watch.ble.GattOperationQueue.OpKey
import app.tandemwear.watch.diag.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase 5.1: one read of the iPhone's Battery Level per session, logged only.
 *
 * Same isolation as [AmsClient]: starts [BatteryProtocol.PROBE_DELAY_MS] after SESSION ACTIVE,
 * waits until ANCS is idle ([AmsPolicy.canProceed]), one GATT operation with the short
 * timeout, and any failure only logs. It never changes the connection state or disconnects.
 */
@SuppressLint("MissingPermission")
internal class BatteryProbe(
    private val scope: CoroutineScope,
    private val operations: GattOperationQueue,
    private val ancsRequestsPending: () -> Int,
    private val lastAncsActivityMs: () -> Long
) {
    private var gatt: BluetoothGatt? = null
    private var job: Job? = null
    private var awaitingRead = false

    fun onAncsSessionActive(g: BluetoothGatt) {
        gatt = g
        job?.cancel()
        val char = g.getService(BatteryProtocol.SERVICE_UUID)?.getCharacteristic(BatteryProtocol.LEVEL_UUID)
        if (char == null) {
            DiagnosticsLog.log(
                "battery: not exposed on this link (service 180f ${if (g.getService(BatteryProtocol.SERVICE_UUID) == null) "absent" else "without 2a19"})"
            )
            return
        }
        job = scope.launch {
            delay(BatteryProtocol.PROBE_DELAY_MS)
            val waitStart = SystemClock.elapsedRealtime()
            val idle = withTimeoutOrNull(MAX_WAIT_MS) {
                while (!AmsPolicy.canProceed(
                        operations.isBusy, operations.pendingCount, ancsRequestsPending(),
                        SystemClock.elapsedRealtime() - lastAncsActivityMs()
                    )
                ) delay(AmsPolicy.IDLE_POLL_MS)
                true
            } ?: false
            if (!idle || gatt !== g) {
                DiagnosticsLog.log("battery: read skipped (ANCS busy for ${SystemClock.elapsedRealtime() - waitStart} ms)")
                return@launch
            }
            awaitingRead = true
            val opStart = SystemClock.elapsedRealtime()
            val ok = operations.enqueue("battery: read level", OpKey.ReadCharacteristic(char.uuid), AmsPolicy.OP_TIMEOUT_MS) {
                g.readCharacteristic(char)
            }.await()
            if (!ok) {
                awaitingRead = false
                DiagnosticsLog.log("battery: read 2a19 failed in ${SystemClock.elapsedRealtime() - opStart} ms — ANCS unaffected")
            }
        }
    }

    /** BLE thread: a successful read of 2a19. */
    fun onLevelRead(value: ByteArray) {
        if (!awaitingRead) return
        awaitingRead = false
        val level = BatteryProtocol.parseLevel(value)
        DiagnosticsLog.log(
            if (level != null) "battery: $level% (2a19 readable)"
            else "battery: 2a19 returned ${value.size} bytes, not a level"
        )
    }

    fun onSessionEnded() {
        job?.cancel()
        job = null
        gatt = null
        awaitingRead = false
    }

    private companion object {
        /** Give up the single probe if ANCS stays busy this long. */
        const val MAX_WAIT_MS = 30_000L
    }
}
