# Apex Cleaner – Project Summary (for NotebookLM / Gemini)

## What it is
Apex Cleaner (app id: com.fitzy.apexcleaner) is an Android "system optimizer" built to be SAFE first. It frees storage, quietly tunes the phone in the background, saves battery without killing other apps, and watches charging. It has two screens designs: a phone app (Material 3) and an Android TV app.

## The aim
Give people the benefits of a cleaner/booster app without the usual dangers of those apps (deleting logins, killing alarms, draining battery by running constantly, scary jargon). Everything is built around trust: it never deletes anything that could log you out or lose settings, never force-closes other apps, and only does heavy work when the phone is charging and idle. The owner is non-technical, so all wording in the app is plain English.

## Features built so far
1. **Safe storage cleaner** – Clears junk caches only. A gatekeeper (called PathGuard) checks every single delete and refuses anything involving app settings, databases, logins or personal media. It has automated tests.
2. **Quiet background tuner** – A light health check runs regularly. A deeper clean runs only while charging and idle. A tiny foreground service does read-only checks at long intervals.
3. **No-kill battery saver** – Slows background activity when the screen is off, pauses and cools down when needed, and keeps a protected list (alarms, notification listeners, widgets, launchers, media apps, plus the user's own whitelist). It never force-stops any app.
4. **One-tap notification cards** – 1-Tap Boost, Clear Hidden Cache, Cool Down Device, and Whitelist App, all available from the notification shade.
5. **Phone dashboard** – Material 3 design with gauges for storage, memory and battery, a one-tap clean, and whitelist switches.
6. **TV dashboard** – Android TV design (Compose TV) with remote-friendly focus: items grow 1.05x with a 3dp border, and lists remember focus position.
7. **Deeper cache cleaning** – Uses Android's official "space request" for other apps' caches, plus a sweep of hidden cache folders on shared storage.
8. **Permission check on launch** – Prompts for anything missing (e.g. "All files access").
9. **Optional Shizuku helper** – For Android 13+, lets the app clear other apps' cache folders more deeply. Skips protected apps and is double-checked by PathGuard. Setup cards on phone and TV.
10. **Fast-Charge Accelerator & Wattage Monitor (phone only)** – Shows live charging wattage (an estimate), charger type, heat, and time to full. When plugged in it pauses only Apex's own background work so charging isn't disturbed. Live lock-screen card with a "Fast-Charge Active" badge. Charging card in the app (dial, speed curve, health panel, limit setting, on/off switch). Default 80% limit alert and an "unplug now" alert at 100%. It cannot cut charger power and never claims to. 14 unit tests cover the maths.
11. **Automatic build & release** – GitHub workflow builds a test app automatically, and a release workflow attaches the app file to tagged releases (can also be started by hand).

## Hard safety rules (never to be broken)
1. Never delete settings, databases, logins or tokens; every delete goes through PathGuard.
2. Never force-stop or kill other apps.
3. Protected apps are never interrupted.
4. Heavy work only via WorkManager with charging + idle conditions.
5. Charging: only pause this app's own work; never claim it can cut charger power.
6. TV: every focusable item must visibly react (scale ≥ 1.03x or ≥ 2dp border).

## How it's organised (code layout)
- `core/` – the engine: PathGuard, SafeCleaner, HealthMonitor, Whitelist/ProtectedApps, PowerPolicy, background Workers, TunerService, Notifications/Actions, OtherAppCaches, Shizuku bridge, Permissions.
- `power/` – charging engine: ChargeMath (pure, tested rules), ChargeEngine, ChargeReader, ChargeNotifier, ChargeSettings, ChargeUi.
- `ui/` – MainViewModel, phone dashboard and charging card, TV dashboard, theme.
- `MainActivity` picks the TV or phone screen automatically.
- Tech: Kotlin, Jetpack Compose (Material 3 for phone, Compose TV 1.0.0 for TV), WorkManager, Gradle. Build check: `./gradlew check` passes (compile, lint, unit tests).

## Status
- Build passing; Android SDK 35 used on the cloud machine.
- **Not yet done:** real-device testing (cleaning, notifications, TV focus, Shizuku with helper running, real wattage readings and lock-screen card); optionally start the light service after a phone restart.
- **Known limits:** cannot clear other apps' private caches without special system permission (Shizuku is the optional workaround); deep clean needs "All files access".

## Ideas for NotebookLM / Gemini to help with
- Improving the look and feel of the phone and TV dashboards.
- Safe new features (e.g. duplicate/large-file finder, app-usage insights, scheduled clean reports) that respect the hard safety rules.
- A real-device test plan and a Play Store readiness checklist (permissions wording, policy compliance for "All files access").
- Ways to explain results to users in everyday language.
