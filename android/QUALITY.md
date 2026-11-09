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

## Window-frequency strategies

- Tools > 选号推演 adds a separate window-frequency card for SSQ/DLT. It uses the local history, numeric issue ordering, and 1–1000 requested periods (30/50/100 shortcuts). The actual sample count and issue range are displayed. Invalid draws and duplicate issues are excluded and counted; conflicting results for one issue fail explicitly. Missing/omission groups remain independent.
- Kotlin-only generation supports balanced, frequency-weighted hot/cold, balanced zones, and at least one consecutive red pair. These are descriptive heuristics, not a fitted prediction model. Presets and custom red/blue counts are validated; each batch contains distinct pools. Different compound pools can overlap in their constituent single tickets.
- Combination counts use both zones and are calculated without expanding tickets. Compound entries still show hits rather than single-ticket prizes. Existing confirmation and prediction-schema V2 storage are reused. Strategy text retains engine version, strategy, requested/actual window, issue range, pool sizes, seed, and group index; no schema migration or new dependency is introduced.
- Statistics and generation run on the compute dispatcher with cancellation checks. Data/parameter changes discard old output, and leaving the screen cancels work. Inputs survive configuration recreation; generated output is transient. Only explicit user actions generate or stage predictions. Existing KL8 tools are unchanged.
- JVM regressions cover window ordering/deduplication, zero frequencies, invalid/conflicting data, boundary histogram bins, sample statistics, all strategies and formats, deterministic seed replay, batch uniqueness, combination counts, and cancellation. Device acceptance: switch lottery/window during work; rotate with custom settings; inspect blue balls in compound results; stage a batch, cancel/reopen confirmation, and save/reload its metadata. There is no Excel import or compound payout calculation in this increment.

## AI frequency integration

- The AI workbench has independent, saved history-frequency settings (enabled by default, 50 periods, balanced preference). SSQ/DLT support 1–1000 periods and all five strategies. KL8 hides these controls and never invokes the SSQ/DLT frequency engine.
- Each request retains one history and parameter snapshot. The workbench uses the latest valid local draw, independent of the draw currently being browsed. Omission facts and frequency statistics are built from this same cutoff. A specified target must follow the cutoff; a blank provisional target remains unconfirmed. Historical builder tests use explicit cutoffs to exclude later draws. Only one omission state is retained during AI preparation rather than an omission map for every historical draw.
- The prompt distinguishes historical draw counts, omissions, user choices, and other users' selections. Compact complete frequency tables (including zeros) are supplemented by rankings and empirical shape distributions. Hot/cold/balanced are preferences; balanced red zones and at least one consecutive red pair are hard rules. These heuristics do not establish improved winning odds.
- Only legal single tickets pass the new parser. Duplicate or out-of-range values, extra tokens and malformed lines invalidate the entire group. Structured recommendation output is never rescued by mining prose; plain numeric fallback is supported only when no recommendation tags exist. Groups are deduplicated, capped to the requested count, checked against selected hard rules and fusion overlap constraints, and returned with rejection reasons. Empty/partial results remain visible; no additional model request is automatically issued to fill missing tickets.
- Results display requested/recognized/retained counts, exclusions, cutoff, requested model and window. Existing confirmation/storage are reused; AIv2 strategy text captures original provider/model and frequencyv1 strategy/window/range. There is no LLM seed or claim of model replayability. Editing window settings cancels and invalidates old results, including late responses.
- Offline checks cover facts/cutoffs/omission consistency, prompt fact separation, strict SSQ/DLT/KL8 parsing, strategy and fusion constraints, partial/empty output, request snapshots, late-response invalidation, saved settings and provenance. No real model API was called. Device acceptance still includes chip/window editing, rotation, KL8 switching, partial-result rendering and the prediction confirmation flow.

## Device acceptance still required

JVM tests verify HTTP cancellation/error handling, paging decisions, snapshot publication, credential scope/AES-GCM, schema SQL migration, prediction semantics, ViewModel failure handling and state restoration. They do not replace Android-device tests of Keystore, backup/restore, Activity/process recreation or rendering/frame timing.

Before public distribution, provide a stable release signing identity and test the SDK migration appropriate to the distribution channel. Google Play submission requires target API 36; this private-install build retains the existing API 34 target. R8 is a separate optimization that requires reflection/serialization regression checks. No release key is generated or committed by this change.
