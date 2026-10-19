package app.tandemwear.watch.ancs

import android.util.Log
import app.tandemwear.watch.ble.AncsConstants
import app.tandemwear.watch.model.AncsNotification
import java.io.ByteArrayOutputStream

/**
 * Reassembles fragmented ANCS Data Source responses into complete notifications.
 *
 * ANCS responses can span multiple GATT notifications due to MTU limits.
 * Bytes are accumulated and the whole buffer is re-parsed on every fragment;
 * responses are small (a few hundred bytes), so this keeps the parser stateless
 * and immune to fragment boundaries falling anywhere inside a tuple.
 *
 * Response format for GetNotificationAttributes (Command 0):
 *   [0]    CommandID = 0x00
 *   [1-4]  NotificationUID (uint32 LE)
 *   [5+]   Attribute tuples: [AttrID(1)] [Length(2 LE)] [Data(Length bytes)]
 *
 * Only one request may be outstanding at a time (see [AttributeRequestQueue]).
 * Data that arrives with no pending request, or for a different UID, is dropped.
 */
class DataSourceAssembler {

    companion object {
        private const val TAG = "DataSourceAssembler"
        private const val HEADER_SIZE = 5
        private const val TUPLE_HEADER_SIZE = 3
    }

    private val buffer = ByteArrayOutputStream()

    private var pending: PendingRequest? = null

    private class PendingRequest(
        val uid: Long,
        val attributes: List<Int>,
        val categoryId: Int,
        val eventFlags: Int
    )

    /** UID of the request currently awaiting a response, or null when idle. */
    val pendingUid: Long? get() = pending?.uid

    /**
     * Start expecting a GetNotificationAttributes response.
     *
     * @param uid The notification UID we requested attributes for
     * @param requestedAttributes The attribute IDs in the order we requested them
     * @param category The category ID from the original NS event
     * @param flags The event flags from the original NS event
     */
    fun expectNotificationAttributes(
        uid: Long,
        requestedAttributes: List<Int>,
        category: Int,
        flags: Int
    ) {
        buffer.reset()
        pending = PendingRequest(uid, requestedAttributes, category, flags)
    }

    /**
     * Feed incoming Data Source bytes. Call this for each onCharacteristicChanged.
     *
     * @return A completed AncsNotification if all attributes have been parsed, null otherwise.
     */
    fun onDataReceived(data: ByteArray): AncsNotification? {
        val request = pending
        if (request == null) {
            Log.w(TAG, "Dropping ${data.size} DS bytes: no pending request")
            return null
        }

        // A new response always starts with CommandID 0 + the requested UID. If the
        // buffer is empty and this fragment doesn't match, it's a stale continuation
        // of an earlier (timed-out) response.
        if (buffer.size() == 0 && !startsResponseFor(data, request.uid)) {
            Log.w(TAG, "Dropping ${data.size} DS bytes: not a response for uid=${request.uid}")
            return null
        }

        buffer.write(data)
        val bytes = buffer.toByteArray()
        if (bytes.size >= HEADER_SIZE && !startsResponseFor(bytes, request.uid)) {
            Log.w(TAG, "Dropping DS response: header does not match uid=${request.uid}")
            buffer.reset()
            return null
        }
        val attributes = parse(bytes, request.attributes.size) ?: return null

        pending = null
        buffer.reset()
        return AncsNotification(
            uid = request.uid,
            appIdentifier = attributes[AncsConstants.ATTR_APP_IDENTIFIER] ?: "",
            appDisplayName = null,
            title = attributes[AncsConstants.ATTR_TITLE] ?: "",
            subtitle = attributes[AncsConstants.ATTR_SUBTITLE],
            message = attributes[AncsConstants.ATTR_MESSAGE] ?: "",
            date = attributes[AncsConstants.ATTR_DATE],
            categoryId = request.categoryId,
            eventFlags = request.eventFlags,
            positiveActionLabel = attributes[AncsConstants.ATTR_POSITIVE_ACTION_LABEL],
            negativeActionLabel = attributes[AncsConstants.ATTR_NEGATIVE_ACTION_LABEL]
        )
    }

    private fun startsResponseFor(data: ByteArray, uid: Long): Boolean {
        if (data.size < HEADER_SIZE) {
            // Header itself split across fragments: accept if what we have is consistent
            return data.isNotEmpty() && data[0].toInt() == AncsConstants.COMMAND_GET_NOTIFICATION_ATTRIBUTES
        }
        return data[0].toInt() == AncsConstants.COMMAND_GET_NOTIFICATION_ATTRIBUTES &&
            readUInt32LE(data, 1) == uid
    }

    /**
     * Parse [expectedCount] attribute tuples from a complete-or-partial response.
     * Returns null while more bytes are needed.
     */
    private fun parse(bytes: ByteArray, expectedCount: Int): Map<Int, String>? {
        if (bytes.size < HEADER_SIZE) return null

        val result = mutableMapOf<Int, String>()
        var offset = HEADER_SIZE
        repeat(expectedCount) {
            if (offset + TUPLE_HEADER_SIZE > bytes.size) return null
            val attrId = bytes[offset].toInt() and 0xFF
            val length = readUInt16LE(bytes, offset + 1)
            offset += TUPLE_HEADER_SIZE
            if (offset + length > bytes.size) return null
            result[attrId] = String(bytes, offset, length, Charsets.UTF_8)
            offset += length
        }
        return result
    }

    private fun readUInt32LE(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF)) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun readUInt16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun reset() {
        pending = null
        buffer.reset()
    }
}
