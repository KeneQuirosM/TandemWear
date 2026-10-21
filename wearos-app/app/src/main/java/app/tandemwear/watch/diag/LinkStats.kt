package app.tandemwear.watch.diag

/**
 * Per-day count of links to the iPhone by who started them and whether ANCS came up on
 * them. Answers "do watch-initiated links ever get ANCS?" (hypothesis A in
 * docs/EMPAREJAMIENTO.md) from normal use, without a dedicated test night.
 * Pure: encoded to a short string for SharedPreferences.
 */
data class LinkStats(
    /** Local date "yyyy-MM-dd" the counts belong to. */
    val day: String,
    val counts: Map<Initiator, Count> = emptyMap()
) {
    enum class Initiator(val code: String) {
        /** Our background (autoConnect) request opened the link shortly before it came up. */
        WATCH("w"),
        /** The iPhone opened it (we attached to an inbound link). */
        IPHONE("i"),
        /** Can't tell: a background request that had been pending for a long time. */
        UNKNOWN("u");

        companion object {
            fun fromCode(code: String) = entries.firstOrNull { it.code == code }
        }
    }

    data class Count(val links: Int = 0, val withAncs: Int = 0)

    /** Adds one finished link; a new day starts from zero. */
    fun record(today: String, initiator: Initiator, hadAncs: Boolean): LinkStats {
        val base = if (today == day) counts else emptyMap()
        val c = base[initiator] ?: Count()
        val next = c.copy(links = c.links + 1, withAncs = c.withAncs + if (hadAncs) 1 else 0)
        return LinkStats(today, base + (initiator to next))
    }

    /** "watch 12 (ANCS 0), iphone 1 (ANCS 1), unknown 0 (ANCS 0)" */
    fun summary(): String = Initiator.entries.joinToString(", ") {
        val c = counts[it] ?: Count()
        "${it.name.lowercase()} ${c.links} (ANCS ${c.withAncs})"
    }

    /** "2026-10-21|w:12:0|i:1:1" */
    fun encode(): String = (listOf(day) + counts.map { (k, v) -> "${k.code}:${v.links}:${v.withAncs}" })
        .joinToString("|")

    companion object {
        /**
         * Who started a link. Android doesn't say; we infer it from how our GATT client got
         * the link. A background request that connects within [RECENT_REQUEST_MS] of being
         * opened was almost surely the watch's doing (the iPhone was already connectable);
         * one that had been pending longer could have been picked up by either side.
         */
        const val RECENT_REQUEST_MS = 60_000L

        fun inferInitiator(viaBackgroundRequest: Boolean, msSinceRequestOpened: Long): Initiator = when {
            !viaBackgroundRequest -> Initiator.IPHONE
            msSinceRequestOpened in 0..RECENT_REQUEST_MS -> Initiator.WATCH
            else -> Initiator.UNKNOWN
        }

        fun decode(text: String?): LinkStats? {
            val parts = text?.split('|') ?: return null
            if (parts.isEmpty() || parts[0].isBlank()) return null
            val counts = parts.drop(1).mapNotNull { part ->
                val f = part.split(':')
                val initiator = f.getOrNull(0)?.let { Initiator.fromCode(it) } ?: return@mapNotNull null
                val links = f.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val ancs = f.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                initiator to Count(links, ancs)
            }.toMap()
            return LinkStats(parts[0], counts)
        }
    }
}
