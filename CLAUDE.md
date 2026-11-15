# Apex Cleaner (com.fitzy.apexcleaner)

Android system optimizer: safe storage cleaner, quiet background tuner, no-kill battery saver,
1-tap notification actions, phone (Material 3) + Android TV (Compose TV 1.0.0) UIs.

## Owner communication (non-technical owner)
- Plain English only. No code, logs, stack traces or diffs in chat unless asked.
- Describe results as what the app now does for the user, in everyday words.
- End each finished task with 2 sentences: what was completed, what comes next.
- Frame choices around how the app looks, feels and works.

## Working rules
- Progress lives in `FEATURE_PROGRESS.md`. Update it after each feature.
- Build all backlog items without asking for confirmation. Stop only when done or ~80% context (then tell owner to run `/clear`).
- Verification is automatic: `./gradlew check` (hook in `.claude/settings.json`). Fix failures yourself.
- Delegate wide searches/audits to subagents. Don't re-scaffold; extend existing code.
- Path-scoped rules: `.claude/rules/android-ui.md`, `.claude/rules/optimizer-engine.md`.

## Layout (package com.fitzy.apexcleaner)
- `core/` engine: PathGuard (delete gatekeeper), SafeCleaner, HealthMonitor, Whitelist/ProtectedApps,
  PowerPolicy (WorkManager + screen state), Workers, TunerService, Notifications/Actions.
- `ui/` MainViewModel, `mobile/MobileDashboard`, `tv/TvDashboard`, `theme/`.
- `MainActivity` picks TV or phone UI from UiModeManager.

## Hard safety rules (never break)
1. Never delete `shared_prefs`, `databases`, logins/tokens or settings. All deletes go through `PathGuard`; keep `PathGuardTest` passing.
2. Never force-stop or kill other apps. No killer loops.
3. Protected apps (alarms, notification listeners, widgets, launchers, media, user whitelist) are never interrupted.
4. Heavy work only via WorkManager with charging + idle constraints.
5. TV: focus must scale >= 1.03x or show >= 2dp accent border; list containers use `focusRestorer()`.
