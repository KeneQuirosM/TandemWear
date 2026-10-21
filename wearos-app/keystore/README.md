# debug.keystore

Fixed **debug** signing key for TandemWear, committed on purpose so that every debug APK
(GitHub Actions or a local build) has the same signature and `adb install -r` can update
the app without uninstalling it (which would erase the stored pairing).

- Store/key password: `android`, alias: `androiddebugkey` (Android's standard debug values)
- SHA-256: `7D:D4:FE:C3:C5:1A:9A:68:31:B5:31:F2:25:CD:3A:8A:CC:93:F9:98:06:C0:F3:5E:9B:6F:33:2B:02:15:BA:5E`
- Not secret and **not for release builds**: a release must use a private key kept out of the repo.
