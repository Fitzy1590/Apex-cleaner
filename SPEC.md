# Apex Cleaner: Feature Spec

## Existing (see FEATURE_PROGRESS.md)
Safe storage cleaner, quiet background tuner, no-kill battery saver, 1-tap notification actions, phone + TV dashboards, optional Shizuku helper.

## Safe Fast-Charge Accelerator & Live Wattage Monitor (phone only)
Owner votes: 1A, 2A, 3A, 4A, 5A+B, 6A, 7B, plus a default charge limit.

| Topic | Decision |
|---|---|
| Start | Automatically every time power is plugged in (`ACTION_POWER_CONNECTED`) |
| Heat | Too warm at 38 C or when Android reports a thermal warning; UI warns and suggests removing the case |
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

## Unified App: Apex Shield & Cleaner (com.fitzy.apexshield), proposed
Merge of AdShield-VPN into Apex Cleaner. Status: AWAITING OWNER VOTE (Phase 1).
- [ ] **Option A [RECOMMENDED MERGED BUILD]**: Local VPN DNS ad firewall + safe storage cleaner + Shizuku cache breakdown + battery thermal monitor + dual phone and TV UI. One app, no duplicated background services.
