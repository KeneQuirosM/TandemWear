package app.tandemwear.watch.ams

import java.util.UUID

/**
 * When and how the watch subscribes to AMS. Pure, so the rules are unit-tested; the GATT
 * side ([app.tandemwear.watch.ble.AmsClient]) only executes them.
 *
 * Hard rules (Phase 3):
 * - AMS only after the ANCS session is active, and never in ANCS's way: each AMS step waits
 *   until the GATT queue is idle and no ANCS attribute request is pending.
 * - AMS failures never touch the ANCS session (no state change, no disconnect).
 */
object AmsPolicy {

    /** When to subscribe. ON_CONNECT now; ON_DEMAND if the writes buzz the watch on every reconnect. */
    enum class StartMode { ON_CONNECT, ON_DEMAND }

    /** Current choice (one constant to flip: see docs/FASE3_PLAN.md). */
    val START_MODE = StartMode.ON_CONNECT

    /** Quiet time after SESSION ACTIVE before the first AMS write (ANCS backlog settles). */
    const val START_DELAY_MS = 5_000L

    /** Poll interval while waiting for ANCS to be idle. */
    const val IDLE_POLL_MS = 200L

    /** ANCS must have been idle this long before an AMS step (a new NS event may be coming). */
    const val IDLE_QUIET_MS = 500L

    /** Short per-operation timeout: a stuck AMS write can't hold ANCS for the default 5 s. */
    const val OP_TIMEOUT_MS = 1_500L

    /** Truncated values worth fetching in full (what the player shows). */
    val FETCH_FULL: Set<Pair<Entity, Int>> = setOf(
        Entity.TRACK to TrackAttribute.TITLE,
        Entity.TRACK to TrackAttribute.ARTIST,
        Entity.TRACK to TrackAttribute.ALBUM
    )

    fun shouldSubscribe(
        mode: StartMode,
        musicEnabled: Boolean,
        ancsSessionActive: Boolean,
        amsAvailable: Boolean,
        demanded: Boolean
    ): Boolean = musicEnabled && ancsSessionActive && amsAvailable &&
        (mode == StartMode.ON_CONNECT || demanded)

    /** True when an AMS step may use the GATT queue now. */
    fun canProceed(queueBusy: Boolean, queuePending: Int, ancsRequestsPending: Int, msSinceAncsActivity: Long): Boolean =
        !queueBusy && queuePending == 0 && ancsRequestsPending == 0 && msSinceAncsActivity >= IDLE_QUIET_MS

    sealed interface Step {
        val label: String

        data class EnableNotifications(val characteristic: UUID, override val label: String) : Step
        data class Write(val characteristic: UUID, val value: ByteArray, override val label: String) : Step {
            override fun equals(other: Any?) =
                other is Write && other.characteristic == characteristic && other.value.contentEquals(value)
            override fun hashCode() = characteristic.hashCode() * 31 + value.contentHashCode()
        }
    }

    /**
     * Apple: subscribe to Entity Update notifications first, then write the entity/attribute
     * pairs. Remote Command notifications tell which commands the player supports.
     * One write per entity with all its attributes (whether a second write replaces or adds
     * is not specified, so never split an entity).
     */
    fun subscriptionPlan(): List<Step> = listOf(
        Step.EnableNotifications(AmsProtocol.ENTITY_UPDATE_UUID, "notify Entity Update"),
        Step.EnableNotifications(AmsProtocol.REMOTE_COMMAND_UUID, "notify Remote Command")
    ) + AmsProtocol.defaultSubscriptions().map { bytes ->
        val entity = Entity.fromId(bytes[0].toInt() and 0xFF)
        Step.Write(AmsProtocol.ENTITY_UPDATE_UUID, bytes, "subscribe ${entity?.name?.lowercase()}")
    }
}
