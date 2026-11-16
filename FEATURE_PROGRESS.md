# FEATURE_PROGRESS.md

## Audit (2026-11-15)
Repo held only a README: no existing app. Owner chose "build fresh here".

## Status: BUILD PASSING
`./gradlew check` (compile, lint, unit tests) passes. Android SDK installed on the cloud machine with owner approval.

## Done (written)
- [x] Gradle/Compose/TV project setup, manifest (phone + TV launcher)
- [x] 1. Safe storage cleaner: PathGuard + SafeCleaner + unit tests
- [x] 2. Background tuner: WorkManager light check, deep clean (charging+idle), light foreground service
- [x] 3. Battery saver: screen-off slowdown, pause/cool-down, protected-apps whitelist, no app killing
- [x] 4. Notification cards: 1-Tap Boost, Clear Hidden Cache, Cool Down Device, Whitelist App
- [x] 5. Phone Material 3 dashboard + TV dashboard (focus scale 1.05x + 3dp border, focusRestorer)
- [x] Guardrails: CLAUDE.md, .claude/settings.json hook, path rules

- [x] Deep clean of OTHER apps' caches via Android's official space-request (OtherAppCaches) + hidden cache folders on shared storage
- [x] Permission check on launch
- [x] Automatic GitHub test-app builder
- [x] Shizuku helper support (optional): clears other apps' cache folders on Android 13+, skips protected apps, guarded by PathGuard plus a second check inside the helper; setup card on phone and TV

## Fast-Charge Accelerator & Wattage Monitor (written, not yet compiled in the app)
Environment note: this session had no Android SDK, so only the pure charging rules were compiled and tested (14 checks pass). Full `./gradlew check` still to run on a machine with the SDK.
- [x] Charging rules + tests (wattage, charger type, heat, time to full, limit alerts)
- [x] Auto start on plug-in / auto restore on unplug; pauses only Apex's own background work
- [x] Live lock-screen notification with Fast-Charge Active badge
- [x] Charging card in the phone app (dial, speed curve, health panel, limit, on/off switch)
- [x] Default 80% limit alert + "unplug now" alert at 100% (cannot cut power itself)
- [ ] Run full build/lint, fix anything it finds
- [ ] Real-device check of wattage readings and the lock-screen card
- [ ] Optional later: start the light service after phone restart

## Pending
- [ ] Real-device test (cleaning, notifications, TV focus, Shizuku with the helper app running)
- [ ] Known limits: cannot clear OTHER apps' private caches without system permission; deep clean needs "All files access"
