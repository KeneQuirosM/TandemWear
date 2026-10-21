package app.tandemwear.watch.ble

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serializes GATT operations: Android allows only one outstanding operation
 * (read, write, descriptor write, MTU request, service discovery) per connection.
 * A second call while one is in flight fails silently by returning false.
 *
 * Each operation is identified by a [key] (e.g. `OpKey.WriteCharacteristic(uuid)`).
 * The GATT callback reports completion with [onComplete]; a callback whose key does
 * not match the in-flight operation is ignored (late callback after a timeout).
 *
 * Threading: every method must be called from [scope], which must be confined to a
 * single thread (or `limitedParallelism(1)`). No internal locking is done.
 */
class GattOperationQueue(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {

    companion object {
        private const val TAG = "GattOperationQueue"
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }

    /** Keys identifying which GATT callback completes an operation. */
    sealed interface OpKey {
        data object Mtu : OpKey
        data object DiscoverServices : OpKey
        data class ReadCharacteristic(val uuid: java.util.UUID) : OpKey
        data class WriteCharacteristic(val uuid: java.util.UUID) : OpKey
        data class WriteDescriptor(val characteristicUuid: java.util.UUID) : OpKey
    }

    private class Operation(
        val name: String,
        val key: OpKey,
        val timeoutMs: Long,
        val start: () -> Boolean,
        val result: CompletableDeferred<Boolean> = CompletableDeferred()
    )

    private val pending = ArrayDeque<Operation>()
    private var current: Operation? = null
    private var timeoutJob: Job? = null

    /** True while an operation is in flight. */
    val isBusy: Boolean get() = current != null

    /** Number of operations waiting behind the current one. */
    val pendingCount: Int get() = pending.size

    /**
     * Queue an operation. [start] issues the GATT call and returns whether Android
     * accepted it. The returned Deferred completes with true on a successful callback,
     * false on rejection, error status, timeout or [clear].
     */
    fun enqueue(name: String, key: OpKey, start: () -> Boolean): Deferred<Boolean> =
        enqueue(name, key, timeoutMs, start)

    /**
     * Same, with a per-operation timeout: low-priority work (AMS) uses a short one so a
     * stuck write can't hold ANCS behind it for the full default timeout.
     */
    fun enqueue(name: String, key: OpKey, timeoutMs: Long, start: () -> Boolean): Deferred<Boolean> {
        val op = Operation(name, key, timeoutMs, start)
        pending.addLast(op)
        if (current == null) startNext()
        return op.result
    }

    /** Report a GATT callback. Ignored unless it matches the in-flight operation. */
    fun onComplete(key: OpKey, success: Boolean) {
        val op = current
        if (op == null || op.key != key) {
            Log.d(TAG, "Ignoring completion for $key (in flight: ${op?.key})")
            return
        }
        finish(op, success)
    }

    /** Fail every queued and in-flight operation (e.g. on disconnect). */
    fun clear() {
        timeoutJob?.cancel()
        timeoutJob = null
        val all = listOfNotNull(current) + pending
        current = null
        pending.clear()
        all.forEach { it.result.complete(false) }
    }

    private fun finish(op: Operation, success: Boolean) {
        timeoutJob?.cancel()
        timeoutJob = null
        current = null
        op.result.complete(success)
        startNext()
    }

    private fun startNext() {
        while (current == null) {
            val op = pending.removeFirstOrNull() ?: return
            current = op
            val accepted = try {
                op.start()
            } catch (e: SecurityException) {
                Log.e(TAG, "${op.name}: missing permission", e)
                false
            }
            if (!accepted) {
                Log.w(TAG, "${op.name}: rejected by the stack")
                current = null
                op.result.complete(false)
                continue
            }
            timeoutJob = scope.launch {
                delay(op.timeoutMs)
                if (current === op) {
                    Log.w(TAG, "${op.name}: timed out after ${op.timeoutMs}ms")
                    finish(op, false)
                }
            }
        }
    }
}
