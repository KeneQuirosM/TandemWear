package app.tandemwear.watch.ancs

import app.tandemwear.watch.model.AncsEvent

/**
 * Decides whether an ANCS Added/Modified event is worth fetching attributes for.
 *
 * The backlog the iPhone replays when a session starts is marked with the PreExisting
 * flag (ANCS spec), so that flag alone separates old from new — no time-based grace
 * window, which used to drop genuinely new notifications for 60 s after every reconnect.
 * Skipping the backlog also avoids the GATT writes that trigger system haptics (gotcha 10).
 */
object AncsEventFilter {

    enum class Decision {
        /** Fetch attributes and show it. */
        FETCH,
        /** Backlog already on the iPhone before this session: ignore. */
        SKIP_PRE_EXISTING
    }

    fun decide(event: AncsEvent): Decision = when {
        // A call that is still ringing when the session starts is worth showing
        event.isIncomingCall -> Decision.FETCH
        event.isPreExisting -> Decision.SKIP_PRE_EXISTING
        else -> Decision.FETCH
    }
}
