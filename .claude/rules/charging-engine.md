---
paths: ["app/src/main/java/**/power/**/*.kt"]
---
# Charging engine rules
- Pure maths and rules live in `ChargeMath` and must stay Android-free and unit-tested (`ChargeMathTest`).
- Fast-charge care pauses ONLY this app's own background work (`PowerPolicy.chargeHold`). Never stop, kill or freeze other apps; never touch alarms, widgets, media, launchers or the whitelist.
- Never claim the app can cut charger power. Say "alert you to unplug". At 100% end fast-charge care and alert.
- Treat wattage as an estimate. Normalise current units/sign (`normalizeMicroAmps`); show "Not available" when unreadable.
- Heat: too warm at >= 38 C or Android thermal status >= MODERATE.
- Service stays tiny: 15 s refresh only while plugged in, 5 min otherwise. Heavy work stays WorkManager + charging + idle.
- Phone only. No TV charging UI (owner choice).
