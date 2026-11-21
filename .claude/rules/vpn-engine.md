---
paths: ["app/src/main/java/**/net/**/*.kt"]
---
# VPN / network rules
- Streaming Priority OFF: the tunnel carries DNS only (one fake DNS address); everything else is outside at full speed.
- Streaming Priority ON (default): ordinary apps, web browsers, Play Store AND streaming/sport/game apps enter the tunnel (owner chose A, 2026-11-21, so ads in those apps are filtered). Streaming, media and game apps are NEVER paced. Banking, push, clock, launchers, notification-listener and alarm apps and the user whitelist stay outside. Limited apps are paced only while a priority app is on screen. IPv6 is left outside the tunnel.
- An app on screen (including split-screen) is never paced; owner rule.
- Pacing (TokenBucket) must suspend, never drop packets. DNS is never paced.
- Banking/payment domains and apps, FCM/push, clock and system apps always bypass filtering (SafeLists wins over blocklists).
- This app is excluded from its own tunnel. No packet inspection of content, no SSL interception.
- Keep Trie, token bucket, DNS parsing and NetPolicy pure and unit-tested.
- Any plain DNS question (UDP port 53) in the full tunnel is answered by our filter, even when an app asks a server like 8.8.8.8 itself. Answers that are aliases (CNAME) of a blocked name are blocked too, except banking/push names and names the user allowed.
- Only real alarm/clock apps stay outside the shield: USE_EXACT_ALARM holders, or SCHEDULE_EXACT_ALARM holders whose name says alarm/clock/timer (that permission is pre-granted to nearly every app).
