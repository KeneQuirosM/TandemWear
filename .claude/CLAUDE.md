# TandemWear (package: app.tandemwear.watch)

iPhone → Wear OS notification mirroring via BLE/ANCS. All local, no cloud relay, no iOS app.
Based on WearBridge (MIT) by Karthik Rajendran — keep its copyright line in LICENSE.

## Commands

```bash
cd wearos-app
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
./gradlew testDebugUnitTest

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.tandemwear.watch/.ui.MainActivity
adb logcat --pid=$(adb shell pidof app.tandemwear.watch)
```

## Hardware

- **Phone**: iPhone 17 Pro Max (iOS 26)
- **Primary Watch**: Google Pixel Watch 2 (Wear OS 5)
- **Secondary Watch**: Samsung Galaxy Watch 5 (Wear OS) — same APK, verified (upstream)
- **TandemWear target**: Samsung Galaxy Watch 4

## Critical Rules

- **Never use `setNotificationSilent()`** — kills heads-up overlays on Wear OS
- **Notification channels are versioned** (`_v7`) — bump version to change settings, Android caches permanently
- **Subscribe Data Source BEFORE Notification Source** — Apple ANCS requirement
- **Skip pre-existing notifications at NS event level** — prevents GATT writes which trigger system haptics

## Rules

- [BLE & ANCS Protocol](/.claude/rules/ble-ancs-protocol.md) — connection flow, Telecom calls, channels, icon mapping
- [Gotchas & Past Bugs](/.claude/rules/gotchas.md) — 21 documented footguns
- [Future Work](/.claude/rules/future-work.md) — full-color icons, multi-device, health data

## Verification

After making changes:
- `cd wearos-app && ./gradlew testDebugUnitTest assembleDebug`
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — Deploy and test on watch
