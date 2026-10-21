package app.tandemwear.watch.ams

/**
 * Snapshot of what the iPhone is playing, built from AMS Entity Update notifications.
 * Immutable: [apply] returns the next state. Values marked truncated should be fetched in
 * full through Entity Attribute ([truncated] lists them).
 */
data class MediaState(
    val playerName: String? = null,
    val playbackInfo: PlaybackInfo? = null,
    /** When [playbackInfo] arrived (ms, monotonic clock of the caller). */
    val playbackInfoReceivedAtMs: Long = 0L,
    /** 0.0 (silent) … 1.0 (full). */
    val volume: Float? = null,
    val queueIndex: Int? = null,
    val queueCount: Int? = null,
    val shuffleMode: QueueMode? = null,
    val repeatMode: QueueMode? = null,
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val durationSeconds: Float? = null,
    val truncated: Set<Pair<Entity, Int>> = emptySet(),
    val supportedCommands: Set<RemoteCommand> = emptySet()
) {
    /** True when some app is the active media player. */
    val hasPlayer: Boolean get() = !playerName.isNullOrEmpty()

    val isPlaying: Boolean get() = playbackInfo?.state == PlaybackState.PLAYING

    /** Current position in seconds, clamped to the duration when known. */
    fun positionSeconds(nowMs: Long): Float? {
        val info = playbackInfo ?: return null
        val position = info.elapsedAt(playbackInfoReceivedAtMs, nowMs)
        return durationSeconds?.let { position.coerceIn(0f, it) } ?: position.coerceAtLeast(0f)
    }

    fun apply(update: EntityUpdate, nowMs: Long): MediaState {
        val key = update.entity to update.attribute
        val truncatedNow = if (update.truncated) truncated + key else truncated - key
        val value = update.value
        val next = when (update.entity) {
            Entity.PLAYER -> when (update.attribute) {
                PlayerAttribute.NAME -> copy(playerName = value.ifEmpty { null })
                PlayerAttribute.PLAYBACK_INFO -> {
                    // Empty value = no active player state (spec: absent attribute)
                    val info = if (value.isEmpty()) null else PlaybackInfo.parse(value) ?: return this
                    copy(playbackInfo = info, playbackInfoReceivedAtMs = nowMs)
                }
                PlayerAttribute.VOLUME -> copy(volume = value.toFloatOrNull()?.coerceIn(0f, 1f))
                else -> this
            }
            Entity.QUEUE -> when (update.attribute) {
                QueueAttribute.INDEX -> copy(queueIndex = value.toIntOrNull())
                QueueAttribute.COUNT -> copy(queueCount = value.toIntOrNull())
                QueueAttribute.SHUFFLE_MODE -> copy(shuffleMode = value.toIntOrNull()?.let { QueueMode.fromId(it) })
                QueueAttribute.REPEAT_MODE -> copy(repeatMode = value.toIntOrNull()?.let { QueueMode.fromId(it) })
                else -> this
            }
            Entity.TRACK -> when (update.attribute) {
                TrackAttribute.ARTIST -> copy(artist = value.ifEmpty { null })
                TrackAttribute.ALBUM -> copy(album = value.ifEmpty { null })
                TrackAttribute.TITLE -> copy(title = value.ifEmpty { null })
                TrackAttribute.DURATION -> copy(durationSeconds = value.toFloatOrNull())
                else -> this
            }
        }
        return next.copy(truncated = truncatedNow)
    }

    /** A full value fetched through Entity Attribute replaces a truncated one. */
    fun applyFullValue(entity: Entity, attribute: Int, value: String, nowMs: Long): MediaState =
        apply(EntityUpdate(entity, attribute, truncated = false, value = value), nowMs)

    fun withSupportedCommands(commands: Set<RemoteCommand>): MediaState = copy(supportedCommands = commands)
}
