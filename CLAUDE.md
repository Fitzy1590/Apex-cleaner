# Apex Cleaner (com.fitzy.apexcleaner)

Android system optimizer: safe storage cleaner, quiet background tuner, no-kill battery saver,
1-tap notification actions, phone (Material 3) + Android TV (Compose TV 1.0.0) UIs.

## Owner communication (non-technical owner)
- Plain English only. No code, logs, stack traces or diffs in chat unless asked.
- Describe results as what the app now does for the user, in everyday words.
- End each finished task with 2 sentences: what was completed, what comes next.
- Frame choices around how the app looks, feels and works.
- Every voting card must include mock screenshots (publish as a visual page), not just text boxes.

## Working rules
- Progress lives in `FEATURE_PROGRESS.md`. Update it after each feature.
- Build all backlog items without asking for confirmation. Stop only when done or ~80% context (then tell owner to run `/clear`).
- Verification is automatic: `./gradlew check` (hook in `.claude/settings.json`). Fix failures yourself.
- Delegate wide searches/audits to subagents. Don't re-scaffold; extend existing code.
- Path-scoped rules: `.claude/rules/android-ui.md`, `optimizer-engine.md`, `vpn-engine.md`, `charging-engine.md`.

## Layout (package com.fitzy.apexcleaner)
- `core/` engine: PathGuard (delete gatekeeper), SafeCleaner, HealthMonitor, Whitelist/ProtectedApps,
  PowerPolicy (WorkManager + screen state), Workers, TunerService, Notifications/Actions.
- `power/` charging engine (ChargeMath, ChargeEngine, ChargeNotifier): rules in `.claude/rules/charging-engine.md`.
- `net/` DNS firewall VPN (ApexVpnService, DomainTrie, SafeLists, TokenBucket, NetPolicy): rules in `.claude/rules/vpn-engine.md`.
- `ui/` MainViewModel, `mobile/MobileDashboard`, `tv/TvDashboard`, `theme/`.
- `MainActivity` picks TV or phone UI from UiModeManager.

## Hard safety rules (never break)
1. Never delete `shared_prefs`, `databases`, logins/tokens or settings. All deletes go through `PathGuard`; keep `PathGuardTest` passing.
2. Never force-stop or kill other apps. No killer loops.
3. Protected apps (alarms, notification listeners, widgets, launchers, media, user whitelist) are never interrupted.
4. Heavy work only via WorkManager with charging + idle constraints.
5. Charging: only pause this app's own work; never claim it can cut charger power.
6. TV: focus must scale >= 1.03x or show >= 2dp accent border; list containers use `focusRestorer()`.
7. Network: never drop packets (pace by suspending); banking, push, clock, system, streaming/media/game and whitelisted apps always bypass the limiter. Owner chose (2026-11-21): with Streaming Priority on, the tunnel carries ALL traffic of every app except banking, push/clock, launchers, notification-listener/alarm apps and whitelisted apps. Streaming, media and game apps ride in the tunnel only so their ads are filtered; they are never slowed.
   The app on screen is never slowed, even in split-screen (owner rule, 2026-11-21).
   Web browsers (Chrome, Samsung Internet, Firefox, etc.) count as ordinary apps even when built in, so ads on web pages are filtered (added 2026-11-21; owner to confirm). With it off, the tunnel carries DNS only.
