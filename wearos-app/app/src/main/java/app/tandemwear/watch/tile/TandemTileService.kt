package app.tandemwear.watch.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.material3.ColorScheme
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
import app.tandemwear.watch.ams.RemoteCommand
import app.tandemwear.watch.ancs.AncsService
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
                    // Tile button: play/pause on the iPhone without opening the app
                    val toggled = requestParams.currentState.lastClickableId == CLICK_MEDIA_TOGGLE
                    if (toggled) sendMediaToggle()
                    var snapshot = SurfaceState.load(this@TandemTileService)
                    // Show the expected state right away; the AMS update refreshes it
                    snapshot.model.music?.takeIf { toggled }?.let { m ->
                        snapshot = snapshot.copy(model = snapshot.model.copy(music = m.copy(isPlaying = !m.isPlaying)))
                    }
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
        val statusText = model.titleWithBattery(statusLabel(context, model))
        val music = model.music
        val openPlayer = clickable(
            action = ActionBuilders.launchAction(
                ComponentName(context, MainActivity::class.java),
                mapOf(MainActivity.EXTRA_DESTINATION to ActionBuilders.stringExtra(MainActivity.DESTINATION_PLAYER))
            ),
            id = "open_player"
        )
        val toggle = clickable(id = CLICK_MEDIA_TOGGLE)
        return materialScope(context, request.deviceConfiguration, allowDynamicTheme = false, defaultColorScheme = TANDEM_SCHEME) {
            primaryLayout(
                titleSlot = {
                    text(statusText.layoutString, typography = Typography.TITLE_SMALL, color = toneColor.argb)
                },
                mainSlot = {
                    if (music != null) musicCard(context, music, openPlayer)
                    else mainContent(context, model, snapshot.hideOnTile, openApp)
                },
                // While music plays the card is the music one: pending notifications go above the button
                labelForBottomSlot = if (music != null && model.pending > 0) {
                    {
                        text(
                            context.resources.getQuantityString(R.plurals.tile_pending, model.pending, model.pending).layoutString,
                            typography = Typography.LABEL_SMALL,
                            color = COLOR_MUTED.argb,
                            maxLines = 1,
                            overflow = LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE
                        )
                    }
                } else {
                    null
                },
                bottomSlot = {
                    if (music != null && music.canTogglePlay) {
                        textEdgeButton(onClick = toggle) {
                            text(context.getString(if (music.isPlaying) R.string.player_pause else R.string.player_play).layoutString)
                        }
                    } else {
                        textEdgeButton(onClick = openApp) {
                            text(context.getString(R.string.tile_open).layoutString)
                        }
                    }
                }
            )
        }
    }

    /** Music card: app and state, title, artist. Tap opens the player. */
    private fun MaterialScope.musicCard(
        context: Context,
        music: TileModel.Music,
        openPlayer: ModifiersBuilders.Clickable
    ): LayoutElement {
        val state = context.getString(if (music.isPlaying) R.string.player_state_playing else R.string.player_state_paused)
        return titleCard(
            onClick = openPlayer,
            title = { text((music.title ?: context.getString(R.string.player_unknown_title)).layoutString, maxLines = 1, overflow = ELLIPSIZE) },
            content = { text((music.artist ?: music.appName ?: "").layoutString, maxLines = 2, overflow = ELLIPSIZE) },
            time = { text(state.layoutString, maxLines = 1) }
        )
    }

    private fun sendMediaToggle() {
        val music = AncsService.sharedMusic.value
        val command = when {
            music.supports(RemoteCommand.TOGGLE_PLAY_PAUSE) -> RemoteCommand.TOGGLE_PLAY_PAUSE
            music.media.isPlaying -> RemoteCommand.PAUSE
            else -> RemoteCommand.PLAY
        }
        try {
            startService(
                Intent(this, AncsService::class.java)
                    .setAction(AncsService.ACTION_MEDIA_COMMAND)
                    .putExtra(AncsService.EXTRA_MEDIA_COMMAND, command.id)
            )
        } catch (e: Exception) {
            // Service not running (no iPhone): nothing to control
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
            title = { text(title.layoutString, maxLines = 1, overflow = ELLIPSIZE) },
            content = { text((message ?: pendingLabel).layoutString, maxLines = 2, overflow = ELLIPSIZE) },
            time = if (message != null) { { text(pendingLabel.layoutString, maxLines = 1) } } else null
        )
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val CLICK_MEDIA_TOGGLE = "media_toggle"

        // Same palette as the app (TandemColors)
        private const val COLOR_CONNECTED = 0xFF8CD3E8.toInt()
        private const val COLOR_RECONNECTING = 0xFFF6C98A.toInt()
        private const val COLOR_IDLE = 0xFFA9ADB1.toInt()
        private const val COLOR_ATTENTION = 0xFFF2B8B5.toInt()
        private const val COLOR_MUTED = 0xFFA9ADB1.toInt()
        private const val ELLIPSIZE = LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE

        /**
         * TandemColors for ProtoLayout Material 3 (its default scheme is lilac): black
         * background, dark grey surfaces, teal accent. Cards use the container colours
         * (dark surface + light text), the edge button the teal primary. Mostly black
         * pixels: cheap on OLED.
         */
        private val TANDEM_SCHEME = ColorScheme(
            primary = 0xFF8CD3E8.toInt().argb,
            primaryDim = 0xFF5FA9BD.toInt().argb,
            primaryContainer = 0xFF1F2426.toInt().argb,
            onPrimary = 0xFF00363F.toInt().argb,
            onPrimaryContainer = 0xFFE3E2E6.toInt().argb,
            secondary = 0xFF8CD3E8.toInt().argb,
            secondaryDim = 0xFF5FA9BD.toInt().argb,
            secondaryContainer = 0xFF2C3134.toInt().argb,
            onSecondary = 0xFF00363F.toInt().argb,
            onSecondaryContainer = 0xFFE3E2E6.toInt().argb,
            tertiary = 0xFF8CD3E8.toInt().argb,
            tertiaryDim = 0xFF5FA9BD.toInt().argb,
            tertiaryContainer = 0xFF0F4A57.toInt().argb,
            onTertiary = 0xFF00363F.toInt().argb,
            onTertiaryContainer = 0xFFBEEBF7.toInt().argb,
            surfaceContainerLow = 0xFF161A1C.toInt().argb,
            surfaceContainer = 0xFF1F2426.toInt().argb,
            surfaceContainerHigh = 0xFF2C3134.toInt().argb,
            onSurface = 0xFFE3E2E6.toInt().argb,
            onSurfaceVariant = 0xFFC4C7CA.toInt().argb,
            outline = 0xFF8A9094.toInt().argb,
            outlineVariant = 0xFF3F4548.toInt().argb,
            background = 0xFF000000.toInt().argb,
            onBackground = 0xFFE3E2E6.toInt().argb,
            error = 0xFFF2B8B5.toInt().argb,
            errorDim = 0xFFD99E9B.toInt().argb,
            errorContainer = 0xFF601410.toInt().argb,
            onError = 0xFF601410.toInt().argb,
            onErrorContainer = 0xFFF2B8B5.toInt().argb
        )

        fun statusLabel(context: Context, model: TileModel): String = context.getString(
            when (model.status) {
                is HomeStatus.Connected -> R.string.tile_connected
                is HomeStatus.Reconnecting -> R.string.home_reconnecting
                is HomeStatus.LinkedNoNotifications -> R.string.home_linked_no_ancs
                is HomeStatus.Disconnected -> R.string.home_disconnected
                HomeStatus.RepairNeeded -> R.string.action_pair_again
                HomeStatus.BluetoothOff -> R.string.home_bluetooth_off
                HomeStatus.NotPaired -> R.string.no_iphone_title
            }
        )
    }
}
