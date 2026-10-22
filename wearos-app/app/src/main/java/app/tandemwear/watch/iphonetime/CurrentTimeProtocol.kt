package app.tandemwear.watch.iphonetime

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Bluetooth SIG Current Time Service (1805), decoded for the diagnostics log only
 * (B2 evaluation, step 1: no rule uses it yet). Pure, unit-tested.
 *
 * Current Time (2a2b, 10 bytes): year (uint16 LE), month, day, hours, minutes, seconds,
 * day of week, fractions256, adjust reason (bit 0 manual, 1 external reference,
 * 2 time zone change, 3 DST change). It is the iPhone's LOCAL time.
 * Local Time Information (2a0f, 2 bytes): time zone (sint8, 15-min steps, -128 = unknown),
 * DST offset (uint8: 0, 2 = +0.5 h, 4 = +1 h, 8 = +2 h, 255 = unknown).
 */
object CurrentTimeProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    val CURRENT_TIME_UUID: UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
    val LOCAL_TIME_INFO_UUID: UUID = UUID.fromString("00002a0f-0000-1000-8000-00805f9b34fb")

    /** After SESSION ACTIVE: before AMS (5 s) and the battery (8 s). */
    const val PROBE_DELAY_MS = 3_000L

    data class CurrentTime(val local: LocalDateTime, val adjustReason: Int) {
        val reasons: List<String>
            get() = buildList {
                if (adjustReason and 0x01 != 0) add("manual")
                if (adjustReason and 0x02 != 0) add("external")
                if (adjustReason and 0x04 != 0) add("zone change")
                if (adjustReason and 0x08 != 0) add("DST change")
            }
    }

    /** Null fields = "unknown" in the characteristic. */
    data class LocalTimeInfo(val zoneQuarterHours: Int?, val dstQuarterHours: Int?) {
        /** Current UTC offset of the iPhone (zone + DST), or null if either is unknown. */
        val offset: ZoneOffset?
            get() {
                val zone = zoneQuarterHours ?: return null
                val dst = dstQuarterHours ?: return null
                return try {
                    ZoneOffset.ofTotalSeconds((zone + dst) * 15 * 60)
                } catch (e: DateTimeException) {
                    null
                }
            }
    }

    fun parseCurrentTime(value: ByteArray): CurrentTime? {
        if (value.size < 10) return null
        val year = (value[0].toInt() and 0xFF) or ((value[1].toInt() and 0xFF) shl 8)
        return try {
            CurrentTime(
                LocalDateTime.of(
                    year, value[2].toInt() and 0xFF, value[3].toInt() and 0xFF,
                    value[4].toInt() and 0xFF, value[5].toInt() and 0xFF, value[6].toInt() and 0xFF,
                    ((value[8].toLong() and 0xFF) * 1_000_000_000L / 256).toInt()
                ),
                adjustReason = value[9].toInt() and 0xFF
            )
        } catch (e: DateTimeException) {
            null
        }
    }

    fun parseLocalTimeInfo(value: ByteArray): LocalTimeInfo? {
        if (value.size < 2) return null
        val zone = value[0].toInt() // signed
        val dst = value[1].toInt() and 0xFF
        return LocalTimeInfo(
            zoneQuarterHours = zone.takeIf { it in -48..56 },
            dstQuarterHours = when (dst) {
                0, 2, 4, 8 -> dst
                else -> null
            }
        )
    }

    /** iPhone clock minus watch clock, when the iPhone's offset is known. */
    fun skew(time: CurrentTime, info: LocalTimeInfo?, watchNowMs: Long): Duration? {
        val offset = info?.offset ?: return null
        return Duration.ofMillis(time.local.toInstant(offset).toEpochMilli() - watchNowMs)
    }

    /** One diagnostics line. */
    fun describe(time: CurrentTime?, info: LocalTimeInfo?, watchNowMs: Long): String = buildString {
        append("time: iPhone ")
        append(time?.local?.toString() ?: "2a2b unreadable")
        if (time != null && time.reasons.isNotEmpty()) append(" (last adjust: ${time.reasons.joinToString()})")
        append(" | zone ")
        append(info?.zoneQuarterHours?.let { q -> ZoneOffset.ofTotalSeconds(q * 15 * 60).id } ?: "unknown")
        append(" dst ")
        append(info?.dstQuarterHours?.let { "+${it * 15} min" } ?: "unknown")
        val skew = if (time != null) skew(time, info, watchNowMs) else null
        append(" | skew ")
        append(skew?.let { "%+.1f s".format(java.util.Locale.ROOT, it.toMillis() / 1000.0) } ?: "n/a (offset unknown)")
    }
}
