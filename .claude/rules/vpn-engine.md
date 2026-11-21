---
paths: ["app/src/main/java/**/vpn/**/*.kt"]
---
# VPN engine rules
- Local VpnService only: DNS filtering on-device. Never send traffic or query logs to any server we run.
- Pure logic (Trie matching, list parsing, categories) stays Android-free and unit-tested.
- Whitelisted and passthrough-list apps bypass the VPN (`addDisallowedApplication`). Banking/streaming never filtered.
- Block by answering NXDOMAIN/0.0.0.0 only; never drop or inspect non-DNS traffic.
- DoH provider is the owner's choice; fall back to system DNS if DoH fails (no broken internet).
- `ShieldVpnService` is the only extra service (Android requires it to be its own). It runs only while the owner has the shield on; no other new background work.
- Query log stays on device, capped in size, and clearable in one tap.
