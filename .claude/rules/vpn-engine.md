---
paths: ["app/src/main/java/**/net/**/*.kt"]
---
# VPN / network rules
- Streaming Priority OFF: the tunnel carries DNS only (one fake DNS address); everything else is outside at full speed.
- Streaming Priority ON (default, owner approved): only the limited apps (ordinary installed apps + Play Store) enter the tunnel with all their traffic. Streaming, media, game, protected, banking, push, clock and system apps are never added. Limited apps are paced only while a priority app is on screen. IPv6 is left outside the tunnel.
- Pacing (TokenBucket) must suspend, never drop packets. DNS is never paced.
- Banking/payment domains and apps, FCM/push, clock and system apps always bypass filtering (SafeLists wins over blocklists).
- This app is excluded from its own tunnel. No packet inspection of content, no SSL interception.
- Keep Trie, token bucket, DNS parsing and NetPolicy pure and unit-tested.
