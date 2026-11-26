# Apex Shield: voting card (2026-11-26, 12-point brief)

Visual version: `VOTING_CARD.html` in the project root.

Most of the brief is already built (see FEATURE_PROGRESS.md). Not asked again: Rust/Go engine (keep current, voted 22 Nov); shorter check hook (would hide failed builds).

## Question 1: the three safe new pieces
- [x] **A (recommended): add all three.** Live Traffic (connections, names, sizes; export .pcapng and .har), Quiet hours (time-window blocking behind a 4-digit PIN), Router script generator (AsusWRT-Merlin, pfSense, OpenWrt).
- [ ] **B: add nothing new.**

## Question 2: clean ads inside web pages (HTTPS). Major change: breaks the "no looking inside web traffic" rule
- [ ] **1 (recommended): skip, keep the safety rule.**
- [x] **2: build for web browsers only.** Setup wizard, own certificate, off by default, banking/payment/video-licence names never opened. Most apps (e.g. Sofascore) ignore user certificates, so they are not affected.

## Question 3: root mode
- [x] **1 (recommended): skip for now.** Untestable here; phone and TV use the helper app, not root.
- [ ] **2: build the optional root switch** (hidden unless root is found).

Reply like `A, 2, 1`, or type your own change.
