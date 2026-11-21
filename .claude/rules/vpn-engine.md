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
