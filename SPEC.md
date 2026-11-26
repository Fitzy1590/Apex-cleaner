# Apex Cleaner: Feature Spec

## Existing (see FEATURE_PROGRESS.md)
Safe storage cleaner, quiet background tuner, no-kill battery saver, 1-tap notification actions, phone + TV dashboards, optional Shizuku helper.

## Safe Fast-Charge Accelerator & Live Wattage Monitor (phone only)
Owner votes: 1A, 2A, 3A, 4A, 5A+B, 6A, 7B, plus a default charge limit.

| Topic | Decision |
|---|---|
| Start | Automatically every time power is plugged in (`ACTION_POWER_CONNECTED`) |
| Heat | Too warm at 42 C or when Android reports a thermal warning; UI warns and suggests removing the case |
| Pauses | Apex's own syncs, checks and cleaning only. Alarms, widgets, media, launchers, whitelist untouched. Other apps never stopped |
| Restore | Unplug (`ACTION_POWER_DISCONNECTED`) or 100% restores normal behaviour |
| Wattage | Battery volts x amps, smoothed, shown as an estimate. Profile: 5W USB / 10W / 15W Fast / 30W Fast / 45W+ Super Fast |
| Lock screen | Live notification: watts, time to full, temperature, "Fast-Charge Active" badge |
| In app | Charging card: wattage dial, speed curve for the session, battery health panel (temp, voltage, current, power, charger type) |
| Time to 100% | Smoothed; "Estimating..." for the first minute; range; slower above 80% |
| TV | Not included |
| Charge limit | Default 80% (adjustable 50 to 100). Alert at the limit and again at 100%, once each per charge |
| 100% | Fast-charge care ends and a "Fully charged: unplug now" alert fires |

### Honest limits
- An app cannot raise charger wattage. It can only reduce heat and competing drain.
- Normal apps cannot switch charging off. The limit and 100% rule alert the owner to unplug. A true automatic cut-off would need phone-maker charge-protection support (not built; possible future option via the Shizuku helper).
- Current/voltage readings vary by phone; unreadable values show "Not available on this phone".
- The service only runs while the app's light service is running (no boot auto-start yet).

### Where it lives
`power/` package: ChargeMath (rules, tested), ChargeReader, ChargeSettings, ChargeEngine (+ PowerEventReceiver), ChargeNotifier, ChargeUi. Card: `ui/mobile/ChargingCard.kt`. Hooks into `PowerPolicy.chargeHold`, `TunerService`, `Workers`.

## Apex Shield & Cleaner upgrade (2026-11-21): vote: Option A chosen
- [x] Option A: Complete Suite (recommended): traffic pacing, ad/tracker DNS firewall, per-app cache breakdown, 3 AM idle maintenance, banking & clock protection list, heat/unplug alarms, TV remote ergonomics
- [ ] Option B: Storage & Battery only (no VPN)
- [ ] Option C: Network VPN engine only
Owner voted Option A. Phase 2 (build) started.

## TV build vote (2026-11-21)
- [x] 1. Finish the TV suite (recommended): instant TV tabs, real speed limiter (wait, never drop), banking list toward 284, home-row tile + remote-key shortcut, dark look check
- [ ] 2. TV polish only (no speed limiter)
- [ ] 3. Speed limiter only
Owner voted 1.

## TV finish build (2026-11-21): owner voted 1 (carry on, decide small things alone, ask only on critical changes)
- [x] Banking list 284+ names; TV Logs tab. Open: per-app banking screen, real-device tests.

## Phase 1 re-vote (2026-11-22): VOTED 1 and 1 (Option A + keep engine)
Brief repeated the Apex Shield & Cleaner upgrade. Most is built; card covers the gaps. See VOTING_CARD.html and .claude/VOTING_CARD.md.
Q1 update:
- [x] 1. Option A: complete update (gaps: Samsung time server + video-licence names on the skip list; real-device checks)
- [ ] 2. Option B: storage and tuner only (removes network shield)
Q2 engine under the shield (major architecture swap, needs a vote):
- [x] 1. Keep current engine, measure first
- [ ] 2. Switch to compiled Rust engine
Kept as is: existing Stop hook (the shorter one in the brief would hide failed builds).

## Instant Mode upgrade (2026-11-26): VOTED A, 1, 1 (2026-11-26)
Audit: nearly the whole brief already exists. Not rebuilt: DNS shield, speed pacing, banking/clock/DRM skip, cache breakdown, 3 AM clean, 42 C heat alert, 80% limit alert, instant tabs, TV violet focus. Rust/Go engine stays "keep current" (voted 2026-11-22).
Genuinely new: (1) Instant Mode = Shizuku sets window/transition/animator scale to 0, saves and restores old values; (2) optional chime at the charge limit; (3) housekeeping: `.claude/rules/tv-ui.md`, `outputStyle: concise`, extra deny rules.
Kept as is: existing Stop hook (brief's `| head -50 || exit 2` would hide failed builds).
Q1 update:
- [x] A: add the three missing pieces (recommended)
- [ ] B: storage and tuner only (removes network shield)
Q2 Instant Mode reach:
- [x] 1: whole phone, off by default (recommended)
- [ ] 2: Apex screens only
Q3 charge chime:
- [x] 1: off until switched on (recommended)
- [ ] 2: on by default

## 12-pillar brief (2026-11-26): WAITING FOR VOTE
Audit: pillars 1 (DNS firewall, trie), 4 (pacing), 5 (cache breakdown, no-kill), 6 (animations, 42 C, chime), 10 (night clean), 11 (284+ banking, clock/DRM, alarms/push), 12 (TV focus) already exist; not rebuilt. Rust/Go core stays "keep current" (voted 22 Nov); existing Stop hook kept.
New: 7 Live Traffic + .pcapng/.har export, 8 PIN-gated Quiet hours, 9 router script generator. Big decisions: 2 HTTPS cosmetic engine (contradicts vpn-engine.md "no SSL interception"), 3 root iptables mode.
Limits: Live Traffic sees only tunnel traffic; .har has names and sizes only unless Q2=2. Apps ignore user CAs by default since Android 7 (Q2 reaches browsers only).
Q1 new pieces:
- [x] A: Live Traffic + Quiet hours + Router script (recommended)
- [ ] B: nothing new
Q2 HTTPS page cleaning:
- [ ] 1: skip, keep safety rule (recommended)
- [x] 2: browsers only, wizard, off by default
Q3 root mode:
- [x] 1: skip for now (recommended)
- [ ] 2: optional root switch

## 12-pillar build (2026-11-26): VOTED Q1 A, Q2 2, Q3 1
Built: Live Traffic (record switch, list, .har/.pcapng export), Quiet hours (PIN, wrap-past-midnight windows, social list + custom names), router script maker (OpenWrt, AsusWRT-Merlin, pfSense steps), browser web page cleaner (local CA in KeyStore, per-site certs, HTTP/1.1 rewriting, CSS/script injection with CSP nonce, QUIC refused for those browsers). Root mode not built (Q3 = 1).
Limits: cleaner needs Android 10+, shield + Streaming priority on (full tunnel), Chrome-family and Samsung Internet only; Live Traffic connection sizes only for apps in the tunnel; `.har` has names and sizes only; router scripts untested on real routers; nothing device-tested yet.
