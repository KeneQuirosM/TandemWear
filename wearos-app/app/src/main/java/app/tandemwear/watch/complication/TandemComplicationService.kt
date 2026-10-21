package app.tandemwear.watch.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.tandemwear.watch.R
import app.tandemwear.watch.tile.SurfaceState
import app.tandemwear.watch.tile.TandemTileService
import app.tandemwear.watch.tile.TileModel
import app.tandemwear.watch.ui.MainActivity
import app.tandemwear.watch.ui.home.HomeStatus

/**
 * SHORT_TEXT complication: TandemWear icon (linked or unlinked rings) + pending count.
 * Updated on demand (UPDATE_PERIOD_SECONDS = 0) when AncsService reports a change.
 */
class TandemComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val snapshot = SurfaceState.load(this)
        return build(snapshot.model)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return build(TileModel(HomeStatus.Connected(null), 3, null))
    }

    private fun build(model: TileModel): ShortTextComplicationData {
        val status = TandemTileService.statusLabel(this, model)
        val pending = resources.getQuantityString(R.plurals.tile_pending, model.pending, model.pending)
        val icon = if (model.tone == TileModel.Tone.CONNECTED) R.drawable.ic_mark_linked else R.drawable.ic_mark_unlinked
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(model.shortText).build(),
            contentDescription = PlainComplicationText.Builder("$status. $pending").build()
        )
            .setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithResource(this, icon)).build())
            .setTapAction(tap)
            .build()
    }
}
