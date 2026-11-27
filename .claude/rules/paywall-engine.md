---
paths: ["app/src/main/java/**/net/mitm/PaywallRules.kt"]
---
# Paywall reader rules. Owner voted 2026-11-27: full, browsers only, off by default.
- Only the browsers and sites the web page cleaner already opens; never other apps. Needs the cleaner on and the certificate installed.
- Only names in `PaywallRules.sites` get the crawler User-Agent and Google Referer. Every other site passes unchanged.
- `MitmEngine.neverOpen` always wins (banking, payment, sign-in, government, DRM, Allow list).
- Piano/Tinypass names are blocked only while the switch is on (`FilterEngine.paywallBlocks`), so subscribers are not affected when it is off.
- Never touch cookies, logins or stored data. Never log decrypted content.
