# FEATURE_PROGRESS.md

## Audit (2026-11-15)
Repo held only a README: no existing app. Owner chose "build fresh here".

## Status: CODE WRITTEN, NOT YET COMPILED
Android SDK is not installed in this cloud machine; installing it needs the owner to accept Google's licence terms. Until then `./gradlew check` has not run.

## Done (written)
- [x] Gradle/Compose/TV project setup, manifest (phone + TV launcher)
- [x] 1. Safe storage cleaner: PathGuard + SafeCleaner + unit tests
- [x] 2. Background tuner: WorkManager light check, deep clean (charging+idle), light foreground service
- [x] 3. Battery saver: screen-off slowdown, pause/cool-down, protected-apps whitelist, no app killing
- [x] 4. Notification cards: 1-Tap Boost, Clear Hidden Cache, Cool Down Device, Whitelist App
- [x] 5. Phone Material 3 dashboard + TV dashboard (focus scale 1.05x + 3dp border, focusRestorer)
- [x] Guardrails: CLAUDE.md, .claude/settings.json hook, path rules

## Pending
- [ ] Install Android SDK, generate Gradle wrapper, run `./gradlew check`, fix errors
- [ ] Real-device test (cleaning, notifications, TV focus)
- [ ] Known limits: cannot clear OTHER apps' private caches without system permission; deep clean needs "All files access"
