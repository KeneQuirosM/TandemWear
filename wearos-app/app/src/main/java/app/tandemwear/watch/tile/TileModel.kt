package app.tandemwear.watch.tile

import app.tandemwear.watch.battery.BatteryPolicy
import app.tandemwear.watch.ui.home.HomeStatus

/**
 * What the Tile and the complication show. Pure (no Android types) so the content rules are
 * unit-tested; the services only render it.
 */
data class TileModel(
    val status: HomeStatus,
    /** TandemWear notifications still in the watch's notification shade. */
    val pending: Int,
    val last: LastNotification?,
    /** The iPhone's now-playing app, when any (AMS). */
    val music: Music? = null,
    /** The iPhone's battery level (%), null when unknown or turned off (Phase 5.2). */
    val iphoneBattery: Int? = null
) {
    /** Tile title: "Connected · 34%" while connected and the level is known. */
    fun titleWithBattery(statusLabel: String): String {
        val battery = BatteryPolicy.label(iphoneBattery)
        return if (status is HomeStatus.Connected && battery != null) "$statusLabel · $battery" else statusLabel
    }

    data class Music(
        val appName: String?,
        val title: String?,
        val artist: String?,
        val isPlaying: Boolean,
        val canTogglePlay: Boolean
    )

    data class LastNotification(
        val appName: String,
        val title: String,
        val message: String,
        /** "Hide message text" was on: only the app name may be shown. */
        val hidden: Boolean
    )

    enum class Tone { CONNECTED, RECONNECTING, IDLE, ATTENTION }

    val tone: Tone
        get() = when (status) {
            is HomeStatus.Connected -> Tone.CONNECTED
            is HomeStatus.Reconnecting, is HomeStatus.LinkedNoNotifications -> Tone.RECONNECTING
            is HomeStatus.Disconnected, HomeStatus.NotPaired -> Tone.IDLE
            HomeStatus.RepairNeeded, HomeStatus.BluetoothOff -> Tone.ATTENTION
        }

    /** First line under the status: sender (or app when hidden). Null = nothing received yet. */
    val lastTitle: String?
        get() = last?.let { if (it.hidden || it.title.isBlank()) it.appName else it.title }

    /** Second line: the message, never when hidden or when [hideOnTile] is on. */
    fun lastMessage(hideOnTile: Boolean): String? =
        last?.takeUnless { it.hidden || hideOnTile }?.message?.takeIf { it.isNotBlank() }

    /** Short text for the complication (≤ 7 chars is what SHORT_TEXT reliably fits). */
    val shortText: String
        get() = when {
            pending > 99 -> "99+"
            else -> pending.coerceAtLeast(0).toString()
        }
}
