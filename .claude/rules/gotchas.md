# Gotchas & Past Bugs

1. **GattCallback parameter naming**: The `onServicesDiscovered` lambda was named the same as the override method, causing infinite recursion (StackOverflowError). Renamed to `onServicesDiscoveredCallback`.

2. **Notification channel caching**: Android permanently caches channel settings. `deleteNotificationChannel()` + recreate with same ID does NOT reset vibration/importance. Must use new channel IDs (versioned).

3. **Don't find the iPhone by name** (removed): users rename iPhones and the BLE name can be truncated. Use the bonded identity address in `PairedPhoneStore`.

4. **An iPhone does not advertise ANCS**: the watch must advertise (ANCS service solicitation) and the iPhone connects from Settings › Bluetooth. Scanning for the iPhone only worked with the removed iOS companion app.

5. **Wear OS full-screen intent**: `setFullScreenIntent()` alone doesn't reliably launch activities on Wear OS. Also call `startActivity()` directly from the service.

6. **macOS `find` commands**: Avoid `find ~` on the home directory — triggers macOS privacy permission dialogs for Music, Desktop, Documents, etc.

7. **`setNotificationSilent()` kills heads-up overlays**: On Wear OS, calling `setNotificationSilent()` on a notification sets `sound=null` which prevents heads-up/overlay display even with IMPORTANCE_HIGH channels. Never use it — let channel settings handle sound, use direct Vibrator API for haptics.

8. **`USE_FULL_SCREEN_INTENT` permission**: On Android 14+ (API 34), this is a special app-op that must be explicitly granted: `adb shell appops set <pkg> USE_FULL_SCREEN_INTENT allow`. Without it, full-screen intents are silently rejected.

9. **Telecom self-managed ConnectionService**: Self-managed calls do NOT use the system's InCallService UI. The app must handle its own call UI. Only `CallProvider` connections (like the native dialer) get the system call screen. Our self-managed connection still gets priority over the charging screen though.

10. **GATT writes trigger system haptics**: During attribute fetching, GATT characteristic writes trigger system haptic feedback on Wear OS. This caused constant vibration on reconnect when pre-existing notifications triggered attribute fetches. Fixed by skipping pre-existing notifications at NS event level — no attribute fetch = no GATT writes = no haptics.

11. **Notification channel sound for overlays**: Channels must have a sound set (even the default `notification_sound`) for heads-up overlays to trigger. Channels with `setSound(null, null)` won't show overlays regardless of importance level.

12. **Double ANCS subscription on rediscovery**: When `discoverServices()` is called again (e.g., to find the companion service), the `onServicesDiscovered` callback must check if ANCS is already subscribed to avoid re-subscribing and resetting the session.

13. **Android auto-grouping silences notifications**: When multiple notifications are posted from the same app, Android auto-groups them and applies `GROUP_ALERT_SUMMARY`, adding the `SILENT` flag to all child notifications. This kills overlays, vibration, and sound. Fix: each notification must have a unique group key (`ancs_{uid}`) to prevent auto-grouping.

14. **Samsung One UI Watch overlay needs MessagingStyle**: Samsung's heads-up overlay only shows message body for `MessagingStyle` notifications. `BigTextStyle` shows content in the notification list but NOT in the overlay popup. Always use `MessagingStyle` for message-type notifications.

15. **Multi-watch ANCS prioritization**: When multiple watches are bonded to the same iPhone, ANCS uses last-in-first-get — the watch that subscribed to Notification Source most recently receives events. Only one watch active at a time.

16. **ANCS doesn't send REMOVE when call is answered**: When an incoming call is answered on iPhone, ANCS sends a MODIFIED event (EventID=1), not REMOVED. REMOVED only comes when the call fully ends. The call overlay must handle MODIFIED to transition to "Active on iPhone" state, otherwise it stays showing "Incoming Call" for the entire call duration.

17. **ANCS is visible before bonding but not usable**: on SDK 36 / iOS 26 discovery shows ANCS and AMS with `bond=NONE`, yet the characteristics "require authorization". Bond before subscribing.

18. **Other bonded iPhones exist**: `B8:45:EB:2A:C6:84` (Apple OUI, `DUAL`, `BONDED`) turned out to be a *different* iPhone known to the watch since May, not the user's (`…84:D5`). Always bond over the LE link; treat other bonded devices as PROBE, never as the paired iPhone.

19. **The stored bond address can look like an RPA**: Android keeps an LE bond under the address used when bonding (pseudo address) and keeps reporting later, resolved RPAs with it. `7F:68:…` (top bits `01`) is expected; don't "fix" it by matching on the public address.

20. **ANCS UIDs are renumbered every session**: after a reconnect the iPhone numbers existing notifications 0..N again. Never derive Android ids from UIDs; use `NotificationIdRegistry` (fresh id per session+UID) and drop all UID state when a session ends. Actions on notifications from an ended session are local-only.

21. **ANCS can take seconds to appear after a reconnect**: the first discovery on a bonded link may return only ~5 services. Wait for Service Changed / encryption or retry with backoff (`AncsDiscoveryPolicy`); never treat it as terminal for the paired iPhone.

22. **iPhone restart re-sends old notifications as new**: iOS restores its notification centre seconds to minutes after the ANCS session is up, without the PreExisting flag (e.g. a reminder from 21:24 the day before at 05:17). `RestoredNotificationRule` sends them to Recent silently when their Date is ≥ 15 min before the session start (first 15 min of a session only) or their fingerprint (`SeenNotifications`, SHA-256 of app+title+text+date) was already shown. In doubt it's NEW: no/odd date, DST gap or ±2 h of a transition, future date (clock/zone mismatch), manual watch time zone, or a time/zone change during the session.

23. **Coroutine `delay()` stalls in Doze**: it counts uptime, which stops in deep sleep. BLE retry timers of seconds stretched to 7–23 min overnight (docs/EMPAREJAMIENTO.md). Pending fix B3.

24. **AMS must never disturb ANCS**: `AmsClient` starts 5 s after SESSION ACTIVE and runs each GATT step only when the queue is empty, no ANCS attribute request is pending and ANCS has been quiet for 500 ms; 1.5 s timeout per AMS op. Its failures only log (`ams: … FAILED — ANCS unaffected`): never change `ConnectionState`, disconnect or feed `KeyLossDetector`. Subscribe Entity Update notifications before writing entity subscriptions (Apple); one write per entity. `AmsPolicy.START_MODE` switches to on-demand if the writes buzz the watch (gotcha 10).

25. **`NotificationActionReceiver` has no intent-filter**: a broadcast with only `setPackage` never reaches it. Always build action intents with `NotificationActionReceiver.intent(...)` (explicit component).

26. **Call phrases ring only when "incoming"**: WhatsApp calls arrive as Social; `CallPhrases` matches per-language phrases (accent/case-insensitive, whole words). Both languages ring only on "incoming"/"calling" phrases: never add a bare "videollamada", "llamada de voz", "video call", "voice call", "ringing" or the ☎ emoji (chat text), and keep the missed/ended exclusions. Unrecognised call-app texts are logged (`call: not a ringing call`) to grow the table.

27. **Other apps' connection attempts show up in `InboundLinkMonitor`**: the `inbound: link down B8:45:EB:2A:C6:84` lines of the 20/21-10 night were the 30 s GATT connect timeouts (`status=147`) of `com.mergecorp.merge`, retrying every ~2 min, and started ~1 s after each of our links came up. Check `dumpsys bluetooth_manager` (`clientConnect(<package>)`) before reading a link-down as a real link, and run tests without other iPhone/ANCS apps installed.
