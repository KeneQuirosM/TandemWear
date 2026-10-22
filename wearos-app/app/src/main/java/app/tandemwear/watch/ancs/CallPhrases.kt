package app.tandemwear.watch.ancs

import java.text.Normalizer

/**
 * Recognises incoming calls that ANCS does not mark as category IncomingCall (WhatsApp
 * sends them as Social, with the call written in the title or message). Pure, unit-tested.
 *
 * Phrases are per language and matched ignoring case and accents ("Llamada" = "llamada",
 * "está" = "esta"). Spanish and English phrases are examples seen or expected from iOS and
 * WhatsApp — por verificar against real notifications; unrecognised call-app notifications
 * that mention a call word are logged ([unrecognisedCallApp]) so the table can grow.
 */
object CallPhrases {

    val CALL_APPS = setOf(
        "net.whatsapp.WhatsApp", "net.whatsapp.WhatsAppSMB",
        "com.apple.mobilephone", "com.apple.facetime"
    )

    /** Normalised (lowercase, no accents) phrases that mean "ringing now", per language. */
    val RINGING: Map<String, List<String>> = mapOf(
        // Only phrases that say "incoming" or "calling you": a bare "videollamada" also
        // appears in chat messages ("¿hacemos una videollamada?")
        "es" to listOf(
            "llamada entrante", "llamada de voz entrante", "llamada de audio entrante",
            "videollamada entrante", "te esta llamando", "esta llamando"
        ),
        // Same rule as Spanish: "incoming" or "calling" only. A bare "video call" or
        // "voice call" also appears in chat messages ("video call later?")
        "en" to listOf(
            "incoming call", "incoming voice call", "incoming video call", "incoming audio call",
            "incoming voice", "incoming video", "incoming audio", "is calling", "calling you"
        )
    )

    /** Same words but the call is over: never ring for these ("Llamada de voz perdida"). */
    val NOT_RINGING: Map<String, List<String>> = mapOf(
        "es" to listOf("perdida", "no contestada", "finalizada", "rechazada"),
        "en" to listOf("missed", "ended", "declined", "unanswered")
    )

    /** Call-related words: an unrecognised call-app text is only logged if it has one (privacy). */
    private val CALL_HINTS = listOf("llam", "call", "video", "voz", "voice", "audio", "facetime")

    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")
    private val RINGING_RX = RINGING.values.flatten().map(::wordRegex)
    private val NOT_RINGING_RX = NOT_RINGING.values.flatten().map(::wordRegex)

    /** Whole words only: "ended" must not match "recommended". */
    private fun wordRegex(phrase: String) = Regex("(^|\\W)" + Regex.escape(phrase) + "($|\\W)")

    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .lowercase()
            .replace(SPACES, " ")
            .trim()

    /** The text says a call is ringing (and not that it was missed or ended). */
    fun hasCallIndicator(text: String): Boolean {
        val n = normalize(text)
        if (NOT_RINGING_RX.any { it.containsMatchIn(n) }) return false
        return RINGING_RX.any { it.containsMatchIn(n) }
    }

    fun isIncomingCall(
        isCallCategory: Boolean,
        appIdentifier: String,
        hasPositiveAction: Boolean,
        title: String,
        message: String
    ): Boolean {
        if (isCallCategory) return true
        val indicator = hasCallIndicator("$message $title")
        return indicator && (hasPositiveAction || appIdentifier in CALL_APPS)
    }

    /**
     * A call app's notification that was not taken for a ringing call: worth logging (normalised
     * text, capped) so missing phrases can be added. Null when there is nothing to report.
     */
    fun unrecognisedCallApp(appIdentifier: String, isIncomingCall: Boolean, title: String, message: String): String? {
        if (isIncomingCall || appIdentifier !in CALL_APPS) return null
        val text = normalize("$title | $message")
        if (CALL_HINTS.none { it in text }) return null
        return text.take(LOG_TEXT_MAX)
    }

    private const val LOG_TEXT_MAX = 60
}
