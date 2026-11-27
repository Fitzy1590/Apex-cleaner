# Apex Shield V4: voting card (2026-11-27)

Visual version: `VOTING_CARD.html` in the project root.

Almost all of the 13-point brief already exists (see FEATURE_PROGRESS.md). New: the paywall reader. Not asked again: Rust/Go engine (keep current, voted 22 Nov), root mode (skip, voted 26 Nov), shorter check hook (hides failed builds).

## Question 1: overall update
- [ ] **1 (recommended): Option A, add the missing piece** (paywall reader only; everything else already works)
- [ ] **2: Option B, storage cleaner + basic DNS filter only** (removes pacing, page cleaner, Live Traffic, Quiet hours, router scripts)

## Question 2: paywall reader (browsers only, off by default, needs the page-cleaner certificate)
- [ ] **1 (recommended): full.** Googlebot look-alike + Google referrer for news sites, Piano/Tinypass names blocked, pop-ups hidden, archive button. Limit: only sites that show crawlers the full article respond; some terms of service forbid it.
- [ ] **2: pop-ups only.** No crawler look-alike.
- [ ] **3: skip.**

Reply like `1, 1`, or type your own change.
