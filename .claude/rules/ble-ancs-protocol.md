# BLE & ANCS Protocol Details

## ANCS Characteristics

| Characteristic | UUID | Role |
|---|---|---|
| Notification Source | `9FBF120D-6301-42D9-8C58-25E699A21DBD` | Subscribe — 8-byte event notifications |
| Control Point | `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9` | Write — request attributes / perform actions |
| Data Source | `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB` | Subscribe — fragmented attribute responses |

**Subscribe to Data Source BEFORE Notification Source** (Apple requirement).

## BLE Connection Flow (see docs/EMPAREJAMIENTO.md)

1. Watch advertises (legacy, connectable, 128-bit ANCS service solicitation; name in scan response) — `WatchAdvertiser`
2. User taps the watch in iPhone › Settings › Bluetooth; the iPhone opens the LE link — seen by `InboundLinkMonitor` (GATT server, no services)
3. `PairingPolicy.onInboundLink` → ATTACH → `BleConnectionManager.attachInbound` opens a GATT client on that link
4. `requestMtu(512)` → `discoverServices()` (ANCS may be visible before bonding, but its characteristics require authorization)
5. Not bonded → `createBond()` over LE → `BOND_BONDED` → store the identity address that newly appears in the bonded list (`PairingPolicy.newlyBondedAddress`) in `PairedPhoneStore`
6. Subscribe DS CCCD → Subscribe NS CCCD → session active (guard against double subscription)
7. NS events → fetch attributes via CP → parse DS → post Android notification

**Every GATT operation goes through `GattOperationQueue`** (one in flight, 5 s timeout). Never call `gatt.read/write/discover/requestMtu` directly. `BleConnectionManager` state lives on a single-threaded dispatcher; GATT callbacks hop onto it. Control Point attribute requests wait for the full Data Source response (or 3 s timeout) before the next one.

## Reconnection

- Never identify the iPhone by name or by a pre-bond address (RPAs rotate). Only the bonded identity address in `PairedPhoneStore`, trusted while the bond exists
- Two paths: (a) watch advertises in RECONNECT mode (~1 s interval) and the iPhone reconnects to it; (b) `connectGatt(autoConnect=true)` to the bonded identity address
- Advertising stops while connected and in `RepairNeeded`
- Exponential backoff 3s → 30s cap; only bonded targets are re-requested
- `BootReceiver` starts the service only if paired + BLUETOOTH_CONNECT granted; adapter ON/OFF restarts the link layer
- Bond loss (iPhone forgot the watch): Android 16 `ACTION_KEY_MISSING` or 2 consecutive key-loss signals (`KeyLossDetector`) → `ConnectionState.RepairNeeded`
- Pairing/connection milestones go to `DiagnosticsLog` (logcat tag `TandemWear`; shown by the debug "TW Probe ANCS" app)

## Silent/Pre-existing Notification Handling

- Pre-existing notifications (`EVENT_FLAG_PRE_EXISTING`) are skipped at NS event level — no GATT writes, no haptics
- 60-second reconnection grace period skips all non-call NS events to avoid vibration storms
- **Never use `setNotificationSilent()`** — it kills heads-up overlays on Wear OS

## Notification Channels

Channels are **versioned** (suffix `_v7` etc.) because Android permanently caches channel settings. Bump version in `AncsApplication.kt` to change settings.

Current channels: `service` (LOW), `incoming_call`, `messages`, `email`, `social`, `schedule`, `other` — all at `IMPORTANCE_HIGH` with system-default sound. Vibration handled via direct Vibrator API only.

## Telecom Framework (Incoming Calls)

1. `AncsConnectionService` registers a `PhoneAccount` with `CAPABILITY_SELF_MANAGED`
2. On cat=1 NS event, `TelecomManager.addNewIncomingCall()` is called immediately
3. System creates `AncsCallConnection` via `onCreateIncomingConnection()`
4. Caller name updated when attributes arrive (~1s later)
5. Answer/Decline sends ANCS action back to iPhone
6. Fallback: CallStyle notification + direct `startActivity()` if Telecom rejects

## Call State Dismissal

When a call is answered on the iPhone, the watch overlay transitions from "Incoming Call" to "Active on iPhone" (auto-dismisses in 5s). Detection:

1. **ANCS MODIFIED**: NS event with EventID=1 for active call UID triggers overlay transition.
2. **ANCS REMOVED**: call ended.
3. **Safety timeout**: 45-second auto-dismiss if no events arrive.

There is no iOS companion app: quick replies and companion call state were removed.

## App Icon Mapping

75 iOS bundle IDs mapped in `AppIconMapper.kt`. 37 notification icons with 25% padding (viewport 32, translate 4). Call screen uses 3 Lucide stroke icons. Watch UI uses Material Icons Extended.
