package app.tandemwear.watch.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.SystemClock
import app.tandemwear.watch.ams.AmsPolicy
import app.tandemwear.watch.battery.BatteryPolicy
import app.tandemwear.watch.battery.BatteryProtocol
import app.tandemwear.watch.ble.GattOperationQueue.OpKey
import app.tandemwear.watch.diag.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The iPhone's battery level (Phase 5.2), on the same GATT link as ANCS.
 *
 * Same isolation as [AmsClient]: starts [BatteryPolicy.START_DELAY_MS] after SESSION ACTIVE,
 * each GATT step waits until ANCS is idle ([AmsPolicy.canProceed]) and uses the short AMS
 * timeout, and any failure only logs. It never changes the connection state or disconnects.
 * All methods run on the BLE dispatcher except [onLevelValue], which may come from a binder.
 */
@SuppressLint("MissingPermission")
internal class BatteryClient(
    private val scope: CoroutineScope,
    private val operations: GattOperationQueue,
    private val ancsRequestsPending: () -> Int,
    private val lastAncsActivityMs: () -> Long,
    /** Debug switch: notifications or polling. */
    private val chosenMode: () -> BatteryPolicy.Mode,
    /** New level, or null when unknown (no session, disabled, not exposed). */
    private val publish: (Int?) -> Unit
) {
    private var gatt: BluetoothGatt? = null
    private var job: Job? = null
    private var subscribed = false

    /** User setting "Batería del iPhone". */
    private var enabled = true

    fun onAncsSessionActive(g: BluetoothGatt) {
        gatt = g
        subscribed = false
        start("session active")
    }

    fun onSessionEnded() {
        job?.cancel()
        job = null
        gatt = null
        subscribed = false
        publish(null)
    }

    fun setEnabled(on: Boolean) {
        if (enabled == on) return
        enabled = on
        DiagnosticsLog.log("battery: ${if (on) "enabled" else "disabled"}")
        if (on) start("enabled") else stop()
    }

    /** A read result or a notification of 2a19. */
    fun onLevelValue(value: ByteArray) {
        if (!enabled) return
        val level = BatteryProtocol.parseLevel(value)
        if (level == null) {
            DiagnosticsLog.log("battery: 2a19 sent ${value.size} bytes, not a level — ignored")
            return
        }
        publish(level)
    }

    private fun levelCharacteristic(g: BluetoothGatt): BluetoothGattCharacteristic? =
        g.getService(BatteryProtocol.SERVICE_UUID)?.getCharacteristic(BatteryProtocol.LEVEL_UUID)

    private fun start(reason: String) {
        val g = gatt ?: return
        if (!enabled || job?.isActive == true) return
        val char = levelCharacteristic(g)
        if (char == null) {
            DiagnosticsLog.log("battery: not exposed on this link — hidden")
            publish(null)
            return
        }
        val canNotify = char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val mode = BatteryPolicy.effectiveMode(chosenMode(), canNotify)
        job = scope.launch {
            delay(BatteryPolicy.START_DELAY_MS)
            DiagnosticsLog.log("battery: starting ($reason), mode=$mode")
            if (!runOp(g, "read level", OpKey.ReadCharacteristic(char.uuid)) { g.readCharacteristic(char) }) return@launch
            if (mode == BatteryPolicy.Mode.SUBSCRIBE && subscribe(g, char)) return@launch
            // POLL (chosen, no notify support, or the subscription failed)
            while (gatt === g && enabled) {
                delay(BatteryPolicy.POLL_INTERVAL_MS)
                if (gatt !== g || !enabled) break
                runOp(g, "poll level", OpKey.ReadCharacteristic(char.uuid)) { g.readCharacteristic(char) }
            }
        }
    }

    private suspend fun subscribe(g: BluetoothGatt, char: BluetoothGattCharacteristic): Boolean {
        val cccd = char.getDescriptor(AncsConstants.CCCD_UUID)
        if (cccd == null || !g.setCharacteristicNotification(char, true)) {
            DiagnosticsLog.log("battery: cannot subscribe (no CCCD) — polling every 15 min")
            return false
        }
        val ok = runOp(g, "subscribe level", OpKey.WriteDescriptor(char.uuid)) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        }
        subscribed = ok
        if (!ok) DiagnosticsLog.log("battery: subscription failed — polling every 15 min, ANCS unaffected")
        return ok
    }

    /** Setting turned off: stop polling and the notifications, and hide the level. */
    private fun stop() {
        job?.cancel()
        job = null
        publish(null)
        val g = gatt ?: return
        if (!subscribed) return
        subscribed = false
        val char = levelCharacteristic(g) ?: return
        g.setCharacteristicNotification(char, false)
        job = scope.launch {
            runOp(g, "unsubscribe level", OpKey.WriteDescriptor(char.uuid)) {
                val cccd = char.getDescriptor(AncsConstants.CCCD_UUID) ?: return@runOp false
                g.writeDescriptor(cccd, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            }
        }
    }

    /** Waits until ANCS is idle, then one GATT operation with the short timeout. False on skip/failure. */
    private suspend fun runOp(g: BluetoothGatt, label: String, key: OpKey, start: () -> Boolean): Boolean {
        val waitStart = SystemClock.elapsedRealtime()
        val idle = withTimeoutOrNull(BatteryPolicy.MAX_WAIT_MS) {
            while (!AmsPolicy.canProceed(
                    operations.isBusy, operations.pendingCount, ancsRequestsPending(),
                    SystemClock.elapsedRealtime() - lastAncsActivityMs()
                )
            ) delay(AmsPolicy.IDLE_POLL_MS)
            true
        } ?: false
        if (!idle || gatt !== g) {
            DiagnosticsLog.log("battery: $label skipped (ANCS busy ${SystemClock.elapsedRealtime() - waitStart} ms)")
            return false
        }
        val opStart = SystemClock.elapsedRealtime()
        val ok = operations.enqueue("battery: $label", key, AmsPolicy.OP_TIMEOUT_MS, start).await()
        DiagnosticsLog.log("battery: $label ${if (ok) "ok" else "failed"} in ${SystemClock.elapsedRealtime() - opStart} ms")
        return ok
    }
}
