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

## Fast-Charge Accelerator & Wattage Monitor (BUILD PASSING)
`./gradlew check` (compile, lint, unit tests incl. 14 charging checks) passes with Android SDK 35 installed on the cloud machine.
- [x] Charging rules + tests (wattage, charger type, heat, time to full, limit alerts)
- [x] Auto start on plug-in / auto restore on unplug; pauses only Apex's own background work
- [x] Live lock-screen notification with Fast-Charge Active badge
- [x] Charging card in the phone app (dial, speed curve, health panel, limit, on/off switch)
- [x] Default 80% limit alert + "unplug now" alert at 100% (cannot cut power itself)
- [x] Run full build/lint, fix anything it finds
- [ ] Real-device check of wattage readings and the lock-screen card
- [ ] Optional later: start the light service after phone restart

## Pending
- [ ] Real-device test (cleaning, notifications, TV focus, Shizuku with the helper app running)
- [ ] Known limits: cannot clear OTHER apps' private caches without system permission; deep clean needs "All files access"

## Audit (2026-11-21): Apex Shield & Cleaner upgrade, Phase 1
Already built: safe cleaner + PathGuard, WorkManager (charging + idle), no-kill battery saver, protected apps, Shizuku helper (clears other apps' caches), charging engine (heat warning at 38 C, limit alerts), phone + TV dashboards.
Partly there: nightly clean exists but has no 3 AM time, no battery > 80%, no 30-minute idle check. Per-app cache breakdown (media / thumbnails / logs) not shown yet. Clock/alarm apps are protected but not listed by name. TV focus is 1.05x + 3dp border but no #7C3AED violet, no sound/haptic, no Quick Settings tile.
Not built at all: local VPN, speed pacing (NetValve), ad/tracker DNS firewall, DoH, banking passthrough list, per-app network bypass, tab switching with no animation.
Notes for the owner to confirm in Phase 2: heat alert is 38 C today; the brief asks for 42 C. Phase 2 must also reconcile the "no force-kill" rule (kept) and rule 4 "heavy work only when charging + idle" with the new 3 AM job (it fits).

## Phase 1 vote: OPTION A chosen (2026-11-21)
- [x] Option A: Complete Suite (NetValve + BlockAds DNS firewall + Shizuku cache breakdown + 3 AM idle maintenance + 284-domain banking & clock whitelist + thermal/unplug alarms + TV D-pad engine)
- [ ] Option B: Storage & Battery only (no VPN)
- [ ] Option C: Network VPN engine only (traffic shaper + DNS firewall)

## Phase 2 build (Option A), started 2026-11-21
Build check PASSED (compile, lint, unit tests) after installing Android SDK 35 on the cloud machine.
- [x] Slice 1 (build passing): heat alert 42 C, 3 AM nightly clean with 3 conditions (plugged, battery > 80%, screen off 30+ min) + tests, clock/system/Google push apps protected by name
- [ ] Slice 2: per-app cache breakdown (media / thumbnails / logs) via Shizuku, phone + TV
- [ ] Slice 3: TV remote engine (violet #7C3AED border, 1.05x, sound/haptic, Quick Settings tile) + instant tab switching
- [ ] Slice 4: NetValve VPN pacing engine (token bucket, DNS exempt, Wi-Fi/mobile/charging/foreground rules)
- [ ] Slice 5: DNS firewall (trie matching, filter lists, DoH) + 284-domain banking list + per-app bypass
- [ ] Slice 6: Control Center screens (phone + TV) tying it together
