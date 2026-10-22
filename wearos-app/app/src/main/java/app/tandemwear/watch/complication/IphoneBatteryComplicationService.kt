package app.tandemwear.watch.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.tandemwear.watch.R
import app.tandemwear.watch.battery.BatteryPolicy
import app.tandemwear.watch.tile.SurfaceState
import app.tandemwear.watch.ui.MainActivity

/**
 * "iPhone battery" complication (Phase 5.2): SHORT_TEXT "34%" with a phone icon, RANGED_VALUE
 * 0–100 %, or MONOCHROMATIC_IMAGE. Updated only when the level changes (SurfaceState).
 * Without a level (not connected, turned off) it shows "–" or no data.
 */
class IphoneBatteryComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, SurfaceState.load(this).model.iphoneBattery)

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, 62)

    private fun build(type: ComplicationType, level: Int?): ComplicationData? {
        val label = BatteryPolicy.label(level)
        val description = text(
            if (label != null) getString(R.string.complication_iphone_battery_desc, label)
            else getString(R.string.complication_iphone_battery_unknown)
        )
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_phone)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(label ?: "–"), description)
                .setMonochromaticImage(icon)
                .setTapAction(openApp())
                .build()
            ComplicationType.RANGED_VALUE -> if (level == null) {
                NoDataComplicationData()
            } else {
                RangedValueComplicationData.Builder(level.toFloat(), 0f, 100f, description)
                    .setText(text(label ?: ""))
                    .setMonochromaticImage(icon)
                    .setTapAction(openApp())
                    .build()
            }
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(icon, description)
                .setTapAction(openApp())
                .build()
            else -> null
        }
    }

    private fun text(value: String) = PlainComplicationText.Builder(value).build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val REQUEST_OPEN_APP = 32
    }
}
