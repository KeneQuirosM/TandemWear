package app.tandemwear.watch.tile

import android.content.ComponentName
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.tandemwear.watch.R
import app.tandemwear.watch.ui.MainActivity
import app.tandemwear.watch.ui.home.HomeStatus
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Tile: connection status, the last notification and how many are pending. Tapping opens
 * the app. No freshness interval: AncsService asks for an update when something changes
 * (SurfaceState.requestUpdate), so the Tile never polls. It doesn't rely on enter/leave
 * events, which Wear OS 6 batches (onRecentInteractionEventsAsync).
 */
class TandemTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                try {
                    val snapshot = SurfaceState.load(this@TandemTileService)
                    val layout = layout(this@TandemTileService, requestParams, snapshot)
                    completer.set(
                        TileBuilders.Tile.Builder()
                            .setResourcesVersion(RESOURCES_VERSION)
                            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                            .build()
                    )
                } catch (e: Exception) {
                    completer.setException(e)
                }
            }
            "TandemTile"
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun layout(
        context: Context,
        request: RequestBuilders.TileRequest,
        snapshot: SurfaceState.Snapshot
    ): LayoutElement {
        val model = snapshot.model
        val openApp = clickable(
            action = ActionBuilders.launchAction(ComponentName(context, MainActivity::class.java)),
            id = "open_app"
        )
        val toneColor = when (model.tone) {
            TileModel.Tone.CONNECTED -> COLOR_CONNECTED
            TileModel.Tone.RECONNECTING -> COLOR_RECONNECTING
            TileModel.Tone.IDLE -> COLOR_IDLE
            TileModel.Tone.ATTENTION -> COLOR_ATTENTION
        }
        val statusText = statusLabel(context, model)
        return materialScope(context, request.deviceConfiguration, allowDynamicTheme = false) {
            primaryLayout(
                titleSlot = {
                    text(statusText.layoutString, typography = Typography.TITLE_SMALL, color = toneColor.argb)
                },
                mainSlot = { mainContent(context, model, snapshot.hideOnTile, openApp) },
                bottomSlot = {
                    textEdgeButton(onClick = openApp) {
                        text(context.getString(R.string.tile_open).layoutString)
                    }
                }
            )
        }
    }

    private fun MaterialScope.mainContent(
        context: Context,
        model: TileModel,
        hideOnTile: Boolean,
        openApp: ModifiersBuilders.Clickable
    ): LayoutElement {
        val title = model.lastTitle
            ?: return text(context.getString(R.string.tile_no_notifications).layoutString, maxLines = 2)
        val message = model.lastMessage(hideOnTile)
        val pendingLabel = context.resources.getQuantityString(R.plurals.tile_pending, model.pending, model.pending)
        return titleCard(
            onClick = openApp,
            title = { text(title.layoutString, maxLines = 1) },
            content = { text((message ?: pendingLabel).layoutString, maxLines = 2) },
            time = if (message != null) { { text(pendingLabel.layoutString, maxLines = 1) } } else null
        )
    }

    companion object {
        private const val RESOURCES_VERSION = "1"

        // Same palette as the app (TandemColors)
        private const val COLOR_CONNECTED = 0xFF8CD3E8.toInt()
        private const val COLOR_RECONNECTING = 0xFFF6C98A.toInt()
        private const val COLOR_IDLE = 0xFFA9ADB1.toInt()
        private const val COLOR_ATTENTION = 0xFFF2B8B5.toInt()

        fun statusLabel(context: Context, model: TileModel): String = context.getString(
            when (model.status) {
                is HomeStatus.Connected -> R.string.tile_connected
                is HomeStatus.Reconnecting -> R.string.home_reconnecting
                is HomeStatus.Disconnected -> R.string.home_disconnected
                HomeStatus.RepairNeeded -> R.string.action_pair_again
                HomeStatus.BluetoothOff -> R.string.home_bluetooth_off
                HomeStatus.NotPaired -> R.string.no_iphone_title
            }
        )
    }
}
