package app.tandemwear.watch.ancs

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * After the iPhone restarts it restores its notification centre some seconds or minutes
 * *after* the ANCS session is up, and ANCS reports those old notifications as new (no
 * PreExisting flag) — e.g. a reminder from 21:24 the day before arriving at 05:17.
 *
 * This rule tells them apart so they can go to Recent silently instead of buzzing.
 * The guiding principle: **when in doubt it's NEW**. A real notification must never be
 * silenced because of a time-zone mismatch, a DST change or a skewed clock; at worst an old
 * one alerts again. Pure (java.time only), unit-tested.
 */
object RestoredNotificationRule {

    enum class Verdict { NEW, RESTORED }

    data class Result(
        val verdict: Verdict,
        /** Short reason for the diagnostics log. */
        val reason: String,
        /** Something about the clocks looks off: treat the rest of this session as NEW. */
        val clockDoubt: Boolean = false
    )

    /** Old notifications are only expected right after a session starts (log: 10 s – 7 min). */
    const val SESSION_WINDOW_MS = 15 * 60_000L

    /** Must be dated at least this long before the session started (covers clock skew). */
    const val AGE_MARGIN_MS = 15 * 60_000L

    /** A date this far in the future means the two clocks or zones disagree. */
    const val FUTURE_TOLERANCE_MS = 2 * 60_000L

    /** Local times this close to a DST/zone transition are ambiguous enough to doubt. */
    const val TRANSITION_DOUBT_MS = 2 * 60 * 60_000L

    /** ANCS Date attribute: local time of the iPhone, "yyyyMMdd'T'HHmmSS" (UTS #35). */
    private val ANCS_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT)

    /**
     * Instant of an ANCS date read in [zone], or null when it can't be trusted: missing,
     * malformed, or a local time that doesn't exist (DST gap). For a local time that exists
     * twice (DST overlap) the later instant is used — the "newer" reading.
     */
    fun parseAncsDate(text: String?, zone: ZoneId): Instant? {
        if (text.isNullOrBlank()) return null
        val local = try {
            LocalDateTime.parse(text.trim(), ANCS_DATE)
        } catch (e: DateTimeParseException) {
            return null
        }
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) return null // skipped by a DST jump
        return offsets.map { local.toInstant(it) }.max()
    }

    /** True when a zone transition (DST, zone rule change) lies within [TRANSITION_DOUBT_MS] of [instant]. */
    fun nearTransition(instant: Instant, zone: ZoneId): Boolean {
        val rules = zone.rules
        val previous = rules.previousTransition(instant.plusMillis(1))?.instant
        val next = rules.nextTransition(instant)?.instant
        return listOfNotNull(previous, next).any { Math.abs(it.toEpochMilli() - instant.toEpochMilli()) <= TRANSITION_DOUBT_MS }
    }

    /**
     * @param dateText the notification's ANCS Date attribute (may be null)
     * @param zone the watch's current time zone (the iPhone's is assumed equal; if not, see clockDoubt)
     * @param sessionStartMs wall-clock time the ANCS session started
     * @param nowMs wall-clock time now
     * @param seenBefore the same notification (app, title, text, date) was already shown
     * @param sessionClockDoubt an earlier check in this session found the clocks unreliable
     * @param watchZoneAutomatic the watch sets its time zone automatically. With a manual zone
     *   it may lag the iPhone's (travel); a watch zone *ahead* of the iPhone's would make fresh
     *   notifications look hours old, which can't be detected from the dates alone — so a
     *   manual zone disables the date rule entirely.
     */
    fun classify(
        dateText: String?,
        zone: ZoneId,
        sessionStartMs: Long,
        nowMs: Long,
        seenBefore: Boolean,
        sessionClockDoubt: Boolean,
        watchZoneAutomatic: Boolean = true
    ): Result {
        if (!watchZoneAutomatic) return Result(Verdict.NEW, "watch time zone is manual")
        val date = parseAncsDate(dateText, zone)
            ?: return Result(Verdict.NEW, if (dateText.isNullOrBlank()) "no date" else "date not usable")
        if (date.toEpochMilli() > nowMs + FUTURE_TOLERANCE_MS) {
            return Result(Verdict.NEW, "date in the future (clock or zone mismatch)", clockDoubt = true)
        }
        if (sessionClockDoubt) return Result(Verdict.NEW, "clock doubt in this session")
        if (nearTransition(date, zone) || nearTransition(Instant.ofEpochMilli(nowMs), zone)) {
            return Result(Verdict.NEW, "near a DST/zone change")
        }
        if (seenBefore) return Result(Verdict.RESTORED, "already shown before")
        val sessionAge = nowMs - sessionStartMs
        if (sessionAge in 0..SESSION_WINDOW_MS && date.toEpochMilli() < sessionStartMs - AGE_MARGIN_MS) {
            return Result(Verdict.RESTORED, "dated ${(sessionStartMs - date.toEpochMilli()) / 60_000} min before the session")
        }
        return Result(Verdict.NEW, "recent")
    }
}
