package app.tandemwear.watch.ui.music

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fits the player on a round screen. Rows are stacked and centred; a row at height y only
 * has the circle's chord at y to live in, so the bottom (volume) row is the tight one.
 * Tries the richest variant first and returns the first that fits without scrolling at the
 * current font scale; if none fits (large font, tiny screen) the screen scrolls instead.
 *
 * Pure geometry in dp, unit-tested for 192–227 dp screens (450×450 px at the possible
 * densities of the Galaxy Watch 4).
 */
object PlayerLayout {

    enum class Variant(
        val playSize: Float,
        val sideSize: Float,
        val volumeButtonSize: Float,
        val gap: Float,
        /** Artist shares the time row (between elapsed and total) instead of its own row. */
        val artistInTimeRow: Boolean
    ) {
        REGULAR(playSize = 56f, sideSize = 48f, volumeButtonSize = 48f, gap = 6f, artistInTimeRow = false),
        COMPACT(playSize = 52f, sideSize = 44f, volumeButtonSize = 44f, gap = 4f, artistInTimeRow = false),
        DENSE(playSize = 48f, sideSize = 44f, volumeButtonSize = 44f, gap = 2f, artistInTimeRow = true)
    }

    data class Plan(
        val variant: Variant,
        /** Percentage text between the volume buttons (else only in the content description). */
        val volumeText: Boolean,
        /** False = doesn't fit: the screen scrolls and shows the "more below" hint. */
        val fits: Boolean,
        /** Usable width per row (dp), keyed by [Row]. */
        val widths: Map<Row, Float>
    )

    enum class Row { TITLE, ARTIST, PROGRESS, CONTROLS, VOLUME }

    /** Space kept for the curved time at the top (TimeText). */
    const val TIME_TEXT_RESERVE = 28f
    /** Inset from the edge of the circle on each side of a row. */
    const val EDGE_INSET = 6f
    /** Rows are measured with this extra margin: text heights are estimates. */
    const val SAFETY = 1.06f

    // Text sizes in sp (TandemType): title 16, body 15, caption 12, label 14
    private const val LINE = 1.3f
    fun titleHeight(fontScale: Float) = 16f * fontScale * LINE
    fun artistHeight(fontScale: Float) = 15f * fontScale * LINE
    fun progressHeight(fontScale: Float) = 6f + 4f + 12f * fontScale * LINE
    fun volumeTextWidth(fontScale: Float) = "100 %".length * 0.62f * 14f * fontScale

    fun controlsWidth(v: Variant) = v.playSize + 2 * v.sideSize + 2 * 6f
    fun volumeWidth(v: Variant, withText: Boolean, fontScale: Float) =
        2 * v.volumeButtonSize + 2 * 8f + if (withText) volumeTextWidth(fontScale) else 0f

    private const val MIN_TEXT_ROW = 72f
    private const val MIN_PROGRESS_ROW = 100f

    /** Width of the circle (radius [r]) usable by a row spanning [top]..[bottom] (centre = 0). */
    fun chord(r: Float, top: Float, bottom: Float): Float {
        val y = if (top <= 0f && bottom >= 0f) 0f else max(abs(top), abs(bottom))
        if (y >= r) return 0f
        return 2 * sqrt(r * r - y * y) - 2 * EDGE_INSET
    }

    fun plan(diameterDp: Float, fontScale: Float, hasArtist: Boolean, hasVolume: Boolean): Plan {
        for (variant in Variant.entries) {
            for (withText in if (hasVolume) listOf(true, false) else listOf(false)) {
                val result = tryFit(diameterDp, fontScale, variant, hasArtist, hasVolume, withText)
                if (result != null) return Plan(variant, withText, fits = true, widths = result)
            }
        }
        // Scrolling: rows pass through the middle, so give them the inscribed square's width
        val square = diameterDp * 0.7f
        return Plan(Variant.COMPACT, volumeText = true, fits = false, widths = Row.entries.associateWith { square })
    }

    private fun tryFit(
        diameter: Float,
        fontScale: Float,
        v: Variant,
        hasArtist: Boolean,
        hasVolume: Boolean,
        withText: Boolean
    ): Map<Row, Float>? {
        val rows = buildList {
            add(Row.TITLE to titleHeight(fontScale))
            if (hasArtist && !v.artistInTimeRow) add(Row.ARTIST to artistHeight(fontScale))
            add(Row.PROGRESS to progressHeight(fontScale))
            add(Row.CONTROLS to v.playSize)
            if (hasVolume) add(Row.VOLUME to v.volumeButtonSize)
        }.map { (row, h) -> row to h * SAFETY }
        val total = rows.sumOf { it.second.toDouble() }.toFloat() + v.gap * (rows.size - 1)
        val r = diameter / 2
        val top = -total / 2
        if (top < -r + TIME_TEXT_RESERVE) return null

        val widths = mutableMapOf<Row, Float>()
        var y = top
        for ((row, h) in rows) {
            val width = chord(r, y, y + h)
            val needed = when (row) {
                Row.TITLE, Row.ARTIST -> MIN_TEXT_ROW
                Row.PROGRESS -> MIN_PROGRESS_ROW
                Row.CONTROLS -> controlsWidth(v)
                Row.VOLUME -> volumeWidth(v, withText, fontScale)
            }
            if (width < needed) return null
            widths[row] = width
            y += h + v.gap
        }
        return widths
    }
}
