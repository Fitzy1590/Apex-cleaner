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

## Merge Audit: Apex Shield & Cleaner (2026-11-21)
Goal: combine AdShield-VPN (com.fitzy.adshield) into this app, renamed Apex Shield & Cleaner (com.fitzy.apexshield).
Findings:
- No AdShield code is in this repo and its repo is not reachable from this session. The whole network/privacy engine (VPN DNS filter, Trie, DoH, passthrough list, query log) must be built fresh here, or the owner supplies the AdShield source.
- No VPN pieces exist yet (no VpnService, no VPN permission/service in the manifest).
- Cleaner engine, Shizuku helper, charging engine and phone/TV dashboards are done and reusable as-is.
- Deep clean runs daily on charging + idle, but is NOT pinned to 3 AM yet.
- Heat warning is 38 C today; the merge brief says 42 C. Needs the owner's pick.
- Name and ID change (apexcleaner -> apexshield) touches the package, manifest, Gradle and tests.
- Governance gaps: no deny rules in settings.json, no subagent-model setting, no vpn-engine rules file. CLAUDE.md is lean (32 lines).

## Merge Vote (PHASE 1): DECIDED 2026-11-21
- [x] Option A [RECOMMENDED MERGED BUILD]: Unified Apex Shield & Cleaner
- Heat warning: 40 C (owner asked for the recommended level for a Samsung Galaxy Z Fold 6). Replaces 38 C.
- AdShield source not supplied: ad blocker is built fresh here.

## Phase 2 steps
- [x] A. Governance (settings deny rules, subagent model, vpn-engine rules)
- [x] B. Heat 40 C (written; not yet compiled, no Android SDK on this machine)
- [x] C. Rename to com.fitzy.apexshield (build passes; SDK reinstalled at /root/android-sdk)
- [x] D. VPN DNS engine: Trie, DNS packet filter, DoH (Cloudflare, Google, Quad9, AdGuard) with plain-DNS fallback, query log with search + 1-tap block, phone card. Starter lists: 112 block rules, 181 passthrough domains (brief asked for 284; not reached). Written + unit tested; NOT yet tried on a real phone.
- [ ] D2. Download bigger block list (e.g. StevenBlack), add the missing passthrough domains, TV shield screen
- [x] E. 3 AM nightly clean (starts at next 3 AM, then daily, charging + idle; tested)
- [x] F. Shield on phone (card + logs) and TV (on/off + secure DNS cards, violet 3dp focus border, 1.05x)
