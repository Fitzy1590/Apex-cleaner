---
paths: ["app/src/main/java/**/ui/**/*.kt"]
---
# UI rules
- Phone: Material 3 (`androidx.compose.material3`), 1-tap safe clean first, gauges for storage/memory/battery, whitelist switches.
- TV: `androidx.tv.material3.*` (Card, Button, Surface) with LazyRow/LazyColumn only.
- TV focus: scale >= 1.03x or border >= 2dp on every focusable; `Modifier.focusRestorer()` on every list container; request initial focus with FocusRequester.
- Plain-English labels, no jargon. Never show raw package names as the only label.
- UI never deletes directly; call core Actions / ViewModel.
