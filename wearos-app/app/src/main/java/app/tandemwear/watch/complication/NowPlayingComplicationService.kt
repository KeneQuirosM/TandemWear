package app.tandemwear.watch.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.tandemwear.watch.R
import app.tandemwear.watch.tile.SurfaceState
import app.tandemwear.watch.ui.MainActivity

/**
 * "Now playing" complication (AMS music): SHORT_TEXT = music icon + ▶ / ❚❚, LONG_TEXT =
 * "Title · Artist", MONOCHROMATIC_IMAGE = music icon. Tap opens the player.
 * Updated on demand together with the Tile (track, play state or player changes only).
 */
class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val model = NowPlayingModel.from(SurfaceState.load(this).model.music)
        return build(request.complicationType, model)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, NowPlayingModel(active = true, isPlaying = true, title = "Labios compartidos", artist = "Maná"))

    private fun build(type: ComplicationType, model: NowPlayingModel): ComplicationData? {
        val description = description(model)
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_music)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(model.shortText), text(description))
                .setMonochromaticImage(icon)
                .setTapAction(openPlayer())
                .build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text(model.longText ?: getString(R.string.player_nothing_playing)), text(description)
            )
                .setMonochromaticImage(icon)
                .setTapAction(openPlayer())
                .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(icon, text(description))
                .setTapAction(openPlayer())
                .build()
            else -> null
        }
    }

    /** TalkBack: "Playing: Title · Artist" / "Nothing playing". */
    private fun description(model: NowPlayingModel): String {
        val long = model.longText ?: return getString(R.string.player_nothing_playing)
        val state = getString(if (model.isPlaying) R.string.player_state_playing else R.string.player_state_paused)
        return "$state: $long"
    }

    private fun text(value: String) = PlainComplicationText.Builder(value).build()

    private fun openPlayer(): PendingIntent = PendingIntent.getActivity(
        this, REQUEST_OPEN_PLAYER,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_PLAYER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val REQUEST_OPEN_PLAYER = 31
    }
}
