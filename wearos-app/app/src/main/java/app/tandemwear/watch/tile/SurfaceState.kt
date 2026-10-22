package app.tandemwear.watch.tile

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.tandemwear.watch.ams.RemoteCommand
import app.tandemwear.watch.ancs.AncsService
import app.tandemwear.watch.complication.NowPlayingComplicationService
import app.tandemwear.watch.complication.TandemComplicationService
import app.tandemwear.watch.data.PairedPhoneStore
import app.tandemwear.watch.data.history.TandemDatabase
import app.tandemwear.watch.data.settings.SettingsRepository
import app.tandemwear.watch.ui.home.HomeStatus
import kotlinx.coroutines.flow.first

/**
 * Shared data for the Tile and the complication ("surfaces"): one snapshot, read on demand
 * when the system asks for an update, so nothing polls in the background.
 */
object SurfaceState {

    /** Snapshot plus whether message text may appear on the Tile. */
    data class Snapshot(val model: TileModel, val hideOnTile: Boolean)

    @SuppressLint("MissingPermission")
    suspend fun load(context: Context): Snapshot {
        val app = context.applicationContext
        val btOn = app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        val paired = try {
            PairedPhoneStore(app).bondedPhone() != null
        } catch (e: SecurityException) {
            false
        }
        val status = HomeStatus.from(AncsService.sharedConnectionState.value, btOn, paired)

        // Mirrored notifications still in the shade (not the ongoing service/call ones)
        val pending = app.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.count { !it.isOngoing && it.id != AncsService.NOTIFICATION_ID_CALL }
            ?: 0

        val lastEntry = TandemDatabase.get(app).historyDao().observeRecent(1).first().firstOrNull()
        val settings = SettingsRepository(app).settings.first()
        val last = lastEntry?.let {
            TileModel.LastNotification(it.appName, it.title, it.message, hidden = it.contentHidden)
        }
        val musicSession = AncsService.sharedMusic.value
        val music = musicSession.takeIf { it.hasPlayer }?.media?.let { m ->
            TileModel.Music(
                appName = m.playerName,
                title = m.title,
                artist = m.artist,
                isPlaying = m.isPlaying,
                canTogglePlay = musicSession.supports(RemoteCommand.TOGGLE_PLAY_PAUSE) ||
                    musicSession.supports(RemoteCommand.PLAY) || musicSession.supports(RemoteCommand.PAUSE)
            )
        }
        return Snapshot(TileModel(status, pending, last, music), hideOnTile = settings.hideContentOnLockScreen)
    }

    /** Ask the system to refresh the Tile and the complication (it re-requests their data). */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        TileService.getUpdater(app).requestUpdate(TandemTileService::class.java)
        listOf(TandemComplicationService::class.java, NowPlayingComplicationService::class.java).forEach {
            ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, it)).requestUpdateAll()
        }
    }
}
