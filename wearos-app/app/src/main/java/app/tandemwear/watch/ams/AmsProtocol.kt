package app.tandemwear.watch.ams

import java.util.UUID

/**
 * Apple Media Service (AMS) protocol: constants, command builders and parsers.
 *
 * Source: Apple "Apple Media Service Reference" (Specification + Appendix A, tables A-1..A-8),
 * checked 2026-10-21. Pure Kotlin, no Android types — not wired to the BLE layer yet
 * (Phase 3). Usage order from the spec: subscribe to Entity Update notifications, then
 * write Entity Update commands; use Entity Attribute only for truncated values.
 */
object AmsProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC")
    /** Writeable, notifiable: write a RemoteCommandID; notifies the list of supported commands. */
    val REMOTE_COMMAND_UUID: UUID = UUID.fromString("9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2")
    /** Writeable with response, notifiable: write EntityID + AttributeIDs; notifies updates. */
    val ENTITY_UPDATE_UUID: UUID = UUID.fromString("2F7CABCE-808D-411F-9A0C-BB92BA96C102")
    /** Readable, writeable: write EntityID + AttributeID, then read the full value. */
    val ENTITY_ATTRIBUTE_UUID: UUID = UUID.fromString("C6B2F38C-23AB-46D8-A6AB-A3A870BBD5D7")

    /** Table A-3. */
    const val ENTITY_UPDATE_FLAG_TRUNCATED = 0x01

    // Errors returned through ATT (spec)
    const val ERROR_INVALID_STATE = 0xA0
    const val ERROR_INVALID_COMMAND = 0xA1
    const val ERROR_ABSENT_ATTRIBUTE = 0xA2

    /** Bytes for an Entity Update command: subscribe to [attributes] of [entity]. */
    fun entityUpdateCommand(entity: Entity, attributes: List<Int>): ByteArray {
        require(attributes.isNotEmpty()) { "At least one attribute" }
        return byteArrayOf(entity.id.toByte(), *attributes.map { it.toByte() }.toByteArray())
    }

    /** The three subscriptions a media remote normally needs. */
    fun defaultSubscriptions(): List<ByteArray> = listOf(
        entityUpdateCommand(
            Entity.PLAYER,
            listOf(PlayerAttribute.NAME, PlayerAttribute.PLAYBACK_INFO, PlayerAttribute.VOLUME)
        ),
        entityUpdateCommand(
            Entity.QUEUE,
            listOf(QueueAttribute.INDEX, QueueAttribute.COUNT, QueueAttribute.SHUFFLE_MODE, QueueAttribute.REPEAT_MODE)
        ),
        entityUpdateCommand(
            Entity.TRACK,
            listOf(TrackAttribute.ARTIST, TrackAttribute.ALBUM, TrackAttribute.TITLE, TrackAttribute.DURATION)
        )
    )

    /** Bytes for a Remote Command write. */
    fun remoteCommand(command: RemoteCommand): ByteArray = byteArrayOf(command.id.toByte())

    /** Bytes for an Entity Attribute write (then read the characteristic). */
    fun entityAttributeRequest(entity: Entity, attribute: Int): ByteArray =
        byteArrayOf(entity.id.toByte(), attribute.toByte())

    /**
     * Parse an Entity Update notification: [EntityID][AttributeID][EntityUpdateFlags][Value…].
     * Returns null for malformed payloads or unknown entities.
     */
    fun parseEntityUpdate(data: ByteArray): EntityUpdate? {
        if (data.size < 3) return null
        val entity = Entity.fromId(data[0].toInt() and 0xFF) ?: return null
        val attribute = data[1].toInt() and 0xFF
        val flags = data[2].toInt() and 0xFF
        val value = String(data, 3, data.size - 3, Charsets.UTF_8)
        return EntityUpdate(entity, attribute, truncated = flags and ENTITY_UPDATE_FLAG_TRUNCATED != 0, value = value)
    }

    /** Parse a Remote Command notification: the commands the current player supports. */
    fun parseSupportedCommands(data: ByteArray): Set<RemoteCommand> =
        data.asList().mapNotNull { RemoteCommand.fromId(it.toInt() and 0xFF) }.toSet()
}

/** Table A-2. */
enum class Entity(val id: Int) {
    PLAYER(0), QUEUE(1), TRACK(2);

    companion object {
        fun fromId(id: Int): Entity? = entries.firstOrNull { it.id == id }
    }
}

/** Table A-4. */
object PlayerAttribute {
    const val NAME = 0
    const val PLAYBACK_INFO = 1
    const val VOLUME = 2
}

/** Table A-5. */
object QueueAttribute {
    const val INDEX = 0
    const val COUNT = 1
    const val SHUFFLE_MODE = 2
    const val REPEAT_MODE = 3
}

/** Table A-8. */
object TrackAttribute {
    const val ARTIST = 0
    const val ALBUM = 1
    const val TITLE = 2
    const val DURATION = 3
}

/** Table A-1. */
enum class RemoteCommand(val id: Int) {
    PLAY(0), PAUSE(1), TOGGLE_PLAY_PAUSE(2), NEXT_TRACK(3), PREVIOUS_TRACK(4),
    VOLUME_UP(5), VOLUME_DOWN(6), ADVANCE_REPEAT_MODE(7), ADVANCE_SHUFFLE_MODE(8),
    SKIP_FORWARD(9), SKIP_BACKWARD(10), LIKE_TRACK(11), DISLIKE_TRACK(12), BOOKMARK_TRACK(13);

    companion object {
        fun fromId(id: Int): RemoteCommand? = entries.firstOrNull { it.id == id }
    }
}

/** PlaybackInfo first field. */
enum class PlaybackState(val id: Int) {
    PAUSED(0), PLAYING(1), REWINDING(2), FAST_FORWARDING(3);

    companion object {
        fun fromId(id: Int): PlaybackState? = entries.firstOrNull { it.id == id }
    }
}

/** Tables A-6 / A-7 share the same values. */
enum class QueueMode(val id: Int) {
    OFF(0), ONE(1), ALL(2);

    companion object {
        fun fromId(id: Int): QueueMode? = entries.firstOrNull { it.id == id }
    }
}

data class EntityUpdate(
    val entity: Entity,
    val attribute: Int,
    val truncated: Boolean,
    val value: String
)

/**
 * PlaybackInfo = "PlaybackState,PlaybackRate,ElapsedTime" (floats use '.').
 * [elapsedSeconds] was captured when the iPhone sent it; see [elapsedAt].
 */
data class PlaybackInfo(
    val state: PlaybackState,
    val rate: Float,
    val elapsedSeconds: Float
) {
    /** Spec: Elapsed + (now − received) × rate, so progress needs no polling. */
    fun elapsedAt(receivedAtMs: Long, nowMs: Long): Float =
        elapsedSeconds + ((nowMs - receivedAtMs).coerceAtLeast(0) / 1000f) * rate

    companion object {
        fun parse(value: String): PlaybackInfo? {
            val parts = value.split(',')
            if (parts.size != 3) return null
            val state = parts[0].trim().toIntOrNull()?.let { PlaybackState.fromId(it) } ?: return null
            val rate = parts[1].trim().toFloatOrNull() ?: return null
            val elapsed = parts[2].trim().toFloatOrNull() ?: return null
            return PlaybackInfo(state, rate, elapsed)
        }
    }
}
