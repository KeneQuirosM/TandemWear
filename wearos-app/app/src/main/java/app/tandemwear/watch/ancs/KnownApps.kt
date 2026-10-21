package app.tandemwear.watch.ancs

/**
 * Display names for iOS bundle IDs (ANCS only sends the bundle ID). Unknown apps fall back
 * to the last component of the bundle ID; Settings › Apps shows the raw ID under the name so
 * new entries can be added here.
 *
 * Only IDs that are known for certain are listed: a wrong ID is harmless but useless.
 * Costa Rican banks, DiDi and Mercado Libre are not listed yet (IDs to be read from the
 * watch, see docs/DECISIONES.md).
 */
object KnownApps {

    fun displayName(bundleId: String): String =
        names[bundleId] ?: fallbackName(bundleId)

    fun isKnown(bundleId: String): Boolean = bundleId in names

    /** "com.example.MyApp" → "MyApp"; empty IDs stay empty. */
    fun fallbackName(bundleId: String): String = bundleId.substringAfterLast('.')

    private val names: Map<String, String> = mapOf(
        // Apple
        "com.apple.MobileSMS" to "Messages",
        "com.apple.mobilephone" to "Phone",
        "com.apple.mobilemail" to "Mail",
        "com.apple.mobilecal" to "Calendar",
        "com.apple.facetime" to "FaceTime",
        "com.apple.reminders" to "Reminders",
        "com.apple.news" to "News",
        "com.apple.mobilenotes" to "Notes",
        "com.apple.Health" to "Health",
        "com.apple.Fitness" to "Fitness",
        "com.apple.weather" to "Weather",
        "com.apple.Maps" to "Maps",
        "com.apple.Music" to "Music",
        "com.apple.stocks" to "Stocks",
        "com.apple.camera" to "Camera",
        "com.apple.Passbook" to "Wallet",
        "com.apple.Preferences" to "Settings",
        "com.apple.mobiletimer" to "Clock",
        "com.apple.podcasts" to "Podcasts",
        "com.apple.findmy" to "Find My",
        "com.apple.Photos" to "Photos",
        "com.apple.TestFlight" to "TestFlight",
        "com.apple.AppStore" to "App Store",
        "com.apple.Passwords" to "Passwords",
        "com.apple.tips" to "Tips",
        "com.apple.shortcuts" to "Shortcuts",
        "com.apple.Home" to "Home",
        "com.apple.iBooks" to "Books",
        "com.apple.tv" to "TV",
        "com.apple.ScreenTimeNotifications" to "Screen Time",

        // Messaging
        "net.whatsapp.WhatsApp" to "WhatsApp",
        "net.whatsapp.WhatsAppSMB" to "WhatsApp Business",
        "ph.telegra.Telegraph" to "Telegram",
        "org.telegram.Telegram" to "Telegram",
        "com.facebook.Messenger" to "Messenger",
        "org.whispersystems.signal" to "Signal",
        "com.slack.Slack" to "Slack",
        "com.hammerandchisel.discord" to "Discord",

        // Social
        "com.atebits.Tweetie2" to "X",
        "com.burbn.instagram" to "Instagram",
        "com.facebook.Facebook" to "Facebook",
        "com.linkedin.LinkedIn" to "LinkedIn",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.toyopagroup.picaboo" to "Snapchat",
        "com.burbn.barcelona" to "Threads",
        "com.reddit.Reddit" to "Reddit",

        // Email & productivity
        "com.google.Gmail" to "Gmail",
        "com.microsoft.Office.Outlook" to "Outlook",
        "com.google.calendar" to "Google Calendar",
        "com.microsoft.skype.teams" to "Teams",
        "notion.id" to "Notion",

        // Google
        "com.google.GoogleMobile" to "Google",
        "com.google.Maps" to "Google Maps",
        "com.google.Drive" to "Google Drive",
        "com.google.photos" to "Google Photos",
        "com.google.Photos" to "Google Photos",
        "com.google.Home" to "Google Home",
        "com.google.chrome.ios" to "Chrome",
        "com.google.ios.youtube" to "YouTube",

        // Maps, transport and delivery
        "com.waze.iphone" to "Waze",
        "com.ubercab.UberClient" to "Uber",
        "com.ubercab.UberEats" to "Uber Eats",

        // Entertainment
        "com.netflix.Netflix" to "Netflix",
        "com.spotify.client" to "Spotify",
        "com.shazam.Shazam" to "Shazam",
        "com.amazon.aiv.AIVApp" to "Prime Video",

        // Other
        "com.microsoft.xboxapp" to "Xbox",
        "com.github.stormcrow" to "GitHub",
    )
}
