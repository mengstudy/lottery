# Android verification and data compatibility

Use JDK 17 and Android SDK 34. Run from `android/`:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain
```

Windows: use `gradlew.bat`. Keep `GRADLE_USER_HOME`, `ANDROID_USER_HOME`, and temporary directories on the configured development disk. CI runs the same checks and retains reports. Tests use fake data and intercepted HTTP calls; they do not contact lottery or AI services and do not use real credentials.

## Upgrade behavior

- Prediction schema version 2 adds explicit entry kind, KL8 play, blue-ball source and full timestamps. Existing records keep their IDs, numbers, strategy, note and original time text. Their kind remains `LEGACY_UNCONFIRMED`; no play or missing year is invented. They display hits rather than a definitive single-ticket prize. Unknown lottery types are quarantined rather than reassigned to SSQ. No uniqueness constraint prevents saving multiple plans for one issue.
- New predictions require reviewing the target issue and complete numbers before saving. Red-only heart selections are reference pools. Compound and reference pools are not evaluated as single tickets. Batch confirmation is one database transaction.
- Local next-issue suggestions preserve width but are provisional. Old data and year-end boundaries have no automatic issue suggestion; users confirm the official issue before saving. There is no hard-coded annual draw count or holiday calendar.
- Legacy plaintext AI credentials are encrypted using an Android Keystore AES-GCM key before the old preference is removed. Credentials are scoped by provider and endpoint. A provider/endpoint change clears the editor's key. Restored or invalid ciphertext is removed and requires reconfiguration. Both backup formats exclude credential preferences and diagnostic logs.

## Device acceptance still required

JVM tests verify HTTP cancellation/error handling, paging decisions, snapshot publication, credential scope/AES-GCM, schema SQL migration, prediction semantics, ViewModel failure handling and state restoration. They do not replace Android-device tests of Keystore, backup/restore, Activity/process recreation or rendering/frame timing.

Before public distribution, provide a stable release signing identity and test the SDK migration appropriate to the distribution channel. Google Play submission requires target API 36; this private-install build retains the existing API 34 target. R8 is a separate optimization that requires reflection/serialization regression checks. No release key is generated or committed by this change.
