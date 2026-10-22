package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.os.SystemClock
import app.tandemwear.watch.ams.AmsPolicy
import app.tandemwear.watch.ble.GattOperationQueue.OpKey
import app.tandemwear.watch.diag.DiagnosticsLog
import app.tandemwear.watch.iphonetime.CurrentTimeProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * B2 evaluation, step 1: reads the iPhone's Current Time (2a2b) and Local Time Information
 * (2a0f) once per session and only logs them (no rule uses them yet).
 *
 * Same isolation as [AmsClient] and [BatteryClient]: starts [CurrentTimeProtocol.PROBE_DELAY_MS]
 * after SESSION ACTIVE, each read waits until ANCS is idle and uses the short timeout, no
 * writes, failures only log. Runs on the BLE dispatcher.
 */
@SuppressLint("MissingPermission")
internal class IphoneTimeProbe(
    private val scope: CoroutineScope,
    private val operations: GattOperationQueue,
    private val ancsRequestsPending: () -> Int,
    private val lastAncsActivityMs: () -> Long
) {
    private var gatt: BluetoothGatt? = null
    private var job: Job? = null
    private var currentTime: CurrentTimeProtocol.CurrentTime? = null
    /** Watch clock (ms since epoch) when 2a2b arrived, for the skew. */
    private var currentTimeAtMs = 0L
    private var localInfo: CurrentTimeProtocol.LocalTimeInfo? = null

    fun onAncsSessionActive(g: BluetoothGatt) {
        gatt = g
        job?.cancel()
        currentTime = null
        localInfo = null
        val service = g.getService(CurrentTimeProtocol.SERVICE_UUID)
        if (service == null) {
            DiagnosticsLog.log("time: Current Time Service (1805) not on this link")
            return
        }
        val ct = service.getCharacteristic(CurrentTimeProtocol.CURRENT_TIME_UUID)
        val lti = service.getCharacteristic(CurrentTimeProtocol.LOCAL_TIME_INFO_UUID)
        DiagnosticsLog.log("time: 1805 has 2a2b=${ct != null} 2a0f=${lti != null}")
        job = scope.launch {
            delay(CurrentTimeProtocol.PROBE_DELAY_MS)
            if (ct != null) read(g, "2a2b") { g.readCharacteristic(ct) }
            if (lti != null && gatt === g) read(g, "2a0f") { g.readCharacteristic(lti) }
            if (gatt === g) {
                DiagnosticsLog.log(CurrentTimeProtocol.describe(currentTime, localInfo, currentTimeAtMs))
            }
        }
    }

    /** BLE thread: a successful read of 2a2b or 2a0f. */
    fun onRead(uuid: java.util.UUID, value: ByteArray) {
        val hex = value.joinToString("") { "%02x".format(it) }
        when (uuid) {
            CurrentTimeProtocol.CURRENT_TIME_UUID -> {
                currentTimeAtMs = System.currentTimeMillis()
                currentTime = CurrentTimeProtocol.parseCurrentTime(value)
                DiagnosticsLog.log("time: 2a2b raw $hex")
            }
            CurrentTimeProtocol.LOCAL_TIME_INFO_UUID -> {
                localInfo = CurrentTimeProtocol.parseLocalTimeInfo(value)
                DiagnosticsLog.log("time: 2a0f raw $hex")
            }
        }
    }

    fun onSessionEnded() {
        job?.cancel()
        job = null
        gatt = null
    }

    private suspend fun read(g: BluetoothGatt, label: String, start: () -> Boolean) {
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
            DiagnosticsLog.log("time: read $label skipped (ANCS busy ${SystemClock.elapsedRealtime() - waitStart} ms)")
            return
        }
        val key = OpKey.ReadCharacteristic(
            if (label == "2a2b") CurrentTimeProtocol.CURRENT_TIME_UUID else CurrentTimeProtocol.LOCAL_TIME_INFO_UUID
        )
        val opStart = SystemClock.elapsedRealtime()
        val ok = operations.enqueue("time: read $label", key, AmsPolicy.OP_TIMEOUT_MS, start).await()
        if (!ok) DiagnosticsLog.log("time: read $label failed in ${SystemClock.elapsedRealtime() - opStart} ms — ANCS unaffected")
    }

    private companion object {
        const val MAX_WAIT_MS = 30_000L
    }
}
