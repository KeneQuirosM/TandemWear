package app.tandemwear.watch.notify

import java.text.Normalizer
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * A conversation = app + title, as ANCS gives them (the title is the sender or group name).
 * Matching ignores case, accents and extra spaces, so "María " and "maria" are the same.
 * [title] keeps the original spelling for display. Pure, unit-tested.
 */
data class Conversation(val appId: String, val title: String) {

    fun matches(otherAppId: String, otherTitle: String): Boolean =
        appId == otherAppId && normalize(title) == normalize(otherTitle)

    /** One DataStore string: app and title joined by a unit separator (never typed by anyone). */
    fun encode(): String = appId + SEP + title

    companion object {
        private const val SEP = '\u001F'
        private val MARKS = Regex("\\p{Mn}+")
        private val SPACES = Regex("\\s+")

        fun normalize(text: String): String =
            MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().replace(SPACES, " ").trim()

        fun decode(value: String): Conversation? {
            val parts = value.split(SEP)
            if (parts.size != 2 || parts[0].isEmpty() || parts[1].isBlank()) return null
            return Conversation(parts[0], parts[1])
        }

        /** "conversation + until" for the muted set; null if malformed. */
        fun encodeMuted(conversation: Conversation, untilMs: Long): String = conversation.encode() + SEP + untilMs

        fun decodeMuted(value: String): Pair<Conversation, Long>? {
            val cut = value.lastIndexOf(SEP)
            if (cut <= 0) return null
            val until = value.substring(cut + 1).toLongOrNull() ?: return null
            val conversation = decode(value.substring(0, cut)) ?: return null
            return conversation to until
        }
    }
}

/** "Silenciar esta conversación": 15 min, 1 h or until 07:00 of the next day. */
enum class MuteDuration {
    MINUTES_15, HOUR_1, UNTIL_TOMORROW_MORNING;

    /** End of the mute in ms since epoch, from [now] in the watch's time zone. */
    fun untilMillis(now: ZonedDateTime): Long = when (this) {
        MINUTES_15 -> now.plus(Duration.ofMinutes(15))
        HOUR_1 -> now.plus(Duration.ofHours(1))
        // Next calendar day at 07:00 local; ZonedDateTime handles a DST gap at that hour
        UNTIL_TOMORROW_MORNING -> now.toLocalDate().plusDays(1).atTime(MORNING).atZone(now.zone)
    }.toInstant().toEpochMilli()

    companion object {
        val MORNING: LocalTime = LocalTime.of(7, 0)
    }
}
