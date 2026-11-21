---
paths: ["app/src/main/java/**/net/**/*.kt"]
---
# VPN / network rules
- The tunnel carries DNS only (one fake DNS address). All other traffic stays outside it at full speed.
- Pacing (TokenBucket) must suspend, never drop packets. DNS is never paced.
- Banking/payment domains and apps, FCM/push, clock and system apps always bypass filtering (SafeLists wins over blocklists).
- This app is excluded from its own tunnel. No packet inspection of content, no SSL interception.
- Keep Trie, token bucket, DNS parsing and NetPolicy pure and unit-tested.
