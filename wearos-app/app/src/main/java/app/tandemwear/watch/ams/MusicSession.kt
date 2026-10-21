package app.tandemwear.watch.ams

/**
 * Everything the UI needs about the iPhone's music, for the current ANCS session.
 * Pure and immutable; AncsService feeds it the AMS packets.
 */
data class MusicSession(
    /** The iPhone publishes AMS on this link. */
    val available: Boolean = false,
    /** We are subscribed (values below are live). */
    val subscribed: Boolean = false,
    val media: MediaState = MediaState()
) {
    /** Some app is the iPhone's now-playing app (playing or paused). */
    val hasPlayer: Boolean get() = subscribed && media.hasPlayer

    fun onStatus(available: Boolean, subscribed: Boolean): MusicSession =
        // Unsubscribed = values are stale: drop them
        if (subscribed) copy(available = available, subscribed = true)
        else MusicSession(available = available, subscribed = false)

    fun onEntityUpdate(data: ByteArray, nowMs: Long): MusicSession {
        val update = AmsProtocol.parseEntityUpdate(data) ?: return this
        return copy(media = media.apply(update, nowMs))
    }

    fun onSupportedCommands(data: ByteArray): MusicSession =
        copy(media = media.withSupportedCommands(AmsProtocol.parseSupportedCommands(data)))

    fun onFullValue(entity: Entity, attribute: Int, value: String, nowMs: Long): MusicSession =
        copy(media = media.applyFullValue(entity, attribute, value, nowMs))

    /** Whether the UI should offer [command] (the iPhone lists what the player supports). */
    fun supports(command: RemoteCommand): Boolean =
        media.supportedCommands.isEmpty() || command in media.supportedCommands

    companion object {
        /** Nothing known (no session, music off, or AMS absent). */
        val NONE = MusicSession()
    }
}
