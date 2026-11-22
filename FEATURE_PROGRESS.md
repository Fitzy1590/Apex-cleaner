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
- [x] Slice 2 (build passing): per-app cache breakdown (media / thumbnails / temp logs / other), read-only, needs the Shizuku helper; shown on phone and TV
- [x] Slice 3 (build passing): TV violet #7C3AED 3dp focus border, 1.05x grow, click sound + buzz on focus, Quick Settings tile "Apex Clean"
- [x] Slice 4 part 1 (build passing): speed-pacing maths (token bucket that waits instead of dropping, policy for Wi-Fi/mobile, charging, foreground/background, DNS and protected apps never paced) + tests
- [x] Slice 5 (build passing): Ad & tracker shield = local DNS firewall (trie matching, encrypted DNS over HTTPS with plain fallback, built-in starter blocklist, hosts-list parser), banking/push/government passthrough list, banking apps skip the tunnel; switch on phone and TV
- [x] Guardrails: settings.json (deny rules, tool search, Haiku subagents, offline-first check hook), vpn-engine.md rule
- [ ] Still to do: real speed-pacing relay (needs a full tunnel that forwards TCP/UDP; not built, riskiest piece)
- [x] StevenBlack + AdGuard lists download inside the app (weekly on Wi-Fi while charging and idle, or tap "Update ad lists"), plus a recent-blocked network log on the phone card (build passing)
- [ ] Still to do: banking list is about 179 domains, not 284 (I only listed ones I am sure of); per-app banking toggle screen; TV network log
- [x] Phone app now has 4 tabs (Home, Shield, Storage, Apps) that switch instantly with no animation; shield card shows how many banking apps skip the shield; TV shows latest blocked names (build passing)
- [ ] Still to do: tabbed layout on TV
- [ ] Real-device test of the shield (DNS on many phones/TVs), Quick Settings tile, TV sound/buzz

## Auto update (2026-11-21)
- [x] Every change merged to main now builds a numbered release (v1.0.N) on GitHub with the app file attached (build passing)
- [x] The app checks for a newer release when it opens and shows an "Update" card on phone and TV; tapping downloads the new file to install over the old one
- [x] Fixed test-signing key in the project so updates install over each other. First install after this must remove the older test build once.
- [ ] Not silent: Android always asks you to confirm each install

## Ad blocking log rebuilt (2026-11-21)
- [x] Every lookup is remembered on the device (how often, blocked or allowed), busiest first, with a Clear button (build passing)
- [x] Allow / Block buttons on each name (phone) or press-to-cycle cards (TV); choices apply instantly while the shield runs and are kept
- [x] Your Allow beats every ad list; your Block works on anything except banking and push-alert names, which stay protected
- [ ] Waiting on owner test results from the first real-device test
- [x] Real-device test 1 (owner): shield blocks ads (13 seen), ad lists downloaded (262,716 names), cache breakdown and tabs work, 2 banking apps found
- [x] Network Logs full screen: search, "Show blocked only" switch, time + reason (Ads / Analytics / Tracker / TV tracking), Allow / Block / Undo, Clear, Close (build passing)
- [x] New ad lists now apply straight away, no need to switch the shield off and on
- [x] Settings page (phone tab + TV row, build passing): night clean on/off, encrypted DNS on/off, heat warning level 38-46 C, version + check for updates, update ad lists, clear network log
- [x] High-priority "Phone is getting hot" alert while charging, once per hot spell (uses the heat level from Settings)

## TV build vote (2026-11-21): OPTION 1 chosen
- [x] 1. Finish the TV suite
- [ ] 2. TV polish only
- [ ] 3. Speed limiter only
- [x] TV tabs: Home, Shield, Storage, Settings, instant switch (written, NOT yet build-checked: no Android tools on this cloud machine today)
- [x] Streaming Priority limiter (owner chose: build, ON by default). Build passing incl. a test of the relay against a real local server. Not yet tested on a real TV.
- [x] Banking list grown from about 179 to about 243 names (only ones I am sure of)
- [x] TV home-row tile already exists (launcher banner); remote red button = Safe Clean, yellow = 1-Tap Boost while app is open (a key that works anywhere needs an accessibility service, not built)
- [x] TV background now #0B0E14 (glass-border cards still to polish)

## Streaming Priority limiter (2026-11-21, build passing)
- [x] While a video, music or game app is on screen, other apps share about 1.5 Mbps down / 0.4 Mbps up; they wait, never drop
- [x] Never limited: streaming apps by name (YouTube, Stremio, Nuvio, Kayo, Nexus, Netflix and more), Android video/music/game apps, banking, push, clock, system, launchers, anything on your Protected apps list
- [x] Switch in Settings (phone + TV), on by default; "Protected apps" list is also the limiter whitelist
- [x] Rules updated (CLAUDE.md rule 7, vpn-engine.md)
- [ ] Needs Usage Access (to know which app is on screen); without it nothing is slowed
- [ ] Known limits: ad shield skips apps outside the limiter; IPv6 not paced; newly installed apps join after the shield restarts; cannot give other apps' CPU away without killing them (forbidden), so this is network priority only
- [ ] Real-device test on TV

## Ads showing in Chrome (2026-11-21)
- [x] Cause: Chrome and Samsung Internet are built-in apps, so the shield skipped them (log showed no Chrome lookups). Browsers now join the shield like ordinary apps (tests written, not yet build-checked: no Android tools on this cloud machine)
- [ ] Owner to confirm: rule 7 now counts browsers as ordinary apps (they are slowed only while a streaming app is on screen)
- [ ] Known limit: ads served from the same name as the page itself (e.g. Facebook feed ads) cannot be blocked by name

## Never slow the app on screen (2026-11-21, build passing)
- [x] Any app you are looking at, including both apps in split-screen, is never slowed, even if a video app is also on screen
- [ ] Android 9 phones (below Android 10) cannot tell which app owns a connection, so the old behaviour applies there

## Check for updates fixed (2026-11-21)
- [x] Button now shows "Checking…", works even while other jobs run, names the version found, and says so when the update server cannot be reached (it used to say "latest version" on a failed check)
- [x] Buttons can no longer get stuck greyed out after a job (build check below)

## Update inside the app (2026-11-21)
- [x] Update card downloads the new version inside the app (shows percent), then opens Android's install screen; no website. Phone and TV (build check below)
- [ ] First time only: Android asks you to allow Apex Cleaner to install updates (one switch), then tap Update again
- [ ] Android always asks you to tap Install; it cannot be silent

## Ads still showing in Chrome, second fix (2026-11-21)
- [x] Cause: Chrome has home-screen widgets, so the "protected apps" check kept it out of the shield even after the first fix. Browsers are now never auto-protected (only if you whitelist one yourself).
- [x] The big ad lists now download by themselves the first time the shield starts, instead of waiting for charging + idle + Wi-Fi (before, only a tiny starter list was active). Not build-checked: no Android tools on this cloud machine.

## More aggressive ad lists (2026-11-21)
- [x] Added Hagezi Pro++ and Peter Lowe lists beside StevenBlack and AdGuard. Lists download on first start (and again if a list is missing). Owner can Allow any wrongly blocked site from Network Logs. Not build-checked.

## Update stuck on "Opening the installer…" (2026-11-21)
- [x] The installer reply is now addressed straight to the app (newer Android refuses vague replies), and the button frees itself after 20 seconds with a clear message instead of staying stuck. Not build-checked.

## Ads in streaming, sport and game apps (2026-11-21, owner chose A)
- [x] Those apps now join the ad shield but are never slowed. Banking, push, clock, launchers, notification/alarm apps and your whitelist stay outside. Not build-checked.
- [ ] Owner to check: streams still start fast and the cricket banner is gone.

## Full build check (2026-11-21, build passing)
- [x] Compile, lint, unit tests and the app file all pass with Android tools present. This covers everything marked "not build-checked" above (TV tabs, browser shield fixes, streaming/game ad filtering, installer fix).
- [ ] Real-device test on a TV

## TV finish (2026-11-21, build passing)
- [x] Banking passthrough list now 288 names (target 284 reached); a test keeps it at 284 or more
- [x] TV "Logs" tab: full list of looked-up names, blocked-only switch, press a name to cycle normal / always allow / always block, Clear button
- [x] Notes corrected: the speed-pacing relay (waits, never drops), the TV tabs and the remote-key shortcuts (red = clean, yellow = boost) are already in the code
- [x] Apps list (phone and TV) now marks known banking apps "Banking app: skips the shield" (locked on); any other bank app can be added with the existing protect switch (build passing)
- [ ] Still open: real-device tests (speed pacing on a phone and TV, shield on TVs, quick-settings tile, TV sound and buzz), dark look check on a real TV

## Always-on card shows ads blocked today (2026-11-21)
- [x] The permanent notification now reads "N ads blocked today" (restarts from zero each day) and says whether ad blocking is on, paused or off
- [x] Buttons on it: Pause 15 min (comes back on by itself) / Resume, and Turn off / Turn on. Paused = ads load normally; banking and other protections are unchanged
- [x] Build passing (compile, lint, tests)
- [ ] Real-device check of the buttons (Turn on needs the one-time VPN permission already given in the app)

## TV closing and freezing with the shield on (2026-11-22)
- [x] Cause 1: the shield start-up (reading every ad list into memory) ran on the screen thread and used far too much memory for a TV, so the app froze and then closed. Ad lists now take about a fiftieth of the memory, are read line by line, and load in the background; ad blocking works instantly from the small built-in list and the big lists join a moment later.
- [x] Cause 2: turning the shield off or restarting it while lookups were in flight could crash the whole app. Those background jobs are now fenced so they can never take the app down.
- [x] Cause 3: unlimited open connections piled up threads on a TV. The shield now caps open connections and closes the quietest first (nothing is dropped from a connection that is in use).
- [ ] Not build-checked: no Android tools on this cloud machine. Real-device test on the TV needed.

## Empty ad boxes still showing (2026-11-22)
- [x] Added the ad-request names used by Yahoo's own ad system (Fantasy/Sports apps) and about 40 more big ad networks to the built-in list, so the app is told 'no ad' up front and can collapse the empty box. Not build-checked.
- [ ] Owner: if a box remains, open Logs right after seeing it and tell me which names are shown as allowed.

## Ads-blocked count dropped (2026-11-22)
- [x] Logs screen now shows how many names the ad lists hold and how many lookups were seen, so a low count can be traced to 'lists not loaded' versus 'apps not asking'. Not build-checked.
- [ ] Owner chose both ad-box options: tighten blocking (+ test reply style) and a 'Hide leftover boxes' switch (Accessibility). Not built yet.

## Ad boxes: collapse and hide (2026-11-22, owner chose both)
- [x] "Collapse blocked ads" (Settings, on by default): blocked ad names are answered "this site does not exist" so most apps drop the slot instead of leaving an empty frame. Can be switched off if an app misbehaves.
- [x] "Hide leftover ad boxes" (Settings, off by default, phone only): optional Accessibility helper that covers empty boxes labelled as ads with a plain dark/white panel. Reads only item labels and sizes, never text; skips banking, protected, whitelisted, launcher, keyboard and this app. Space stays, box is hidden.
- [x] Unit tests added for the label matcher, size guard and the new reply. Not build-checked (no Android tools here).
- [ ] Real-device check: turn on both switches, enable "Apex Ad Box Hider" in Android Accessibility, open Yahoo Fantasy.

## Black bars from Hide leftover ad boxes (2026-11-22)
- [x] Cause: the cover stayed fixed while the page scrolled, so it slid over real content (seen on Amazon). Covers now come down the moment you scroll and return only after scrolling stops, only cover boxes fully on screen, and take the app's own background colour instead of fixed black/white. Switching the option off in Settings removes them at once.
- [ ] Real-device check on Amazon and Yahoo Fantasy. If covers still hit real content in some app, that app gets excluded.

## Black and white bars still on Amazon (2026-11-22)
- [x] Cause: Amazon marks real product tiles "Sponsored", and the box hider covered them with a flat panel. "Sponsored" no longer counts as an ad label, shopping apps (Amazon, eBay, Walmart, Temu etc.) are skipped, and a box is covered only if all four corners are one flat colour (photos and product tiles are left alone). If the app's colour can't be read, nothing is covered instead of using plain black or white. Build passing.
- [ ] Real-device check on Amazon. If bars remain, the next step is removing the box hider altogether.
