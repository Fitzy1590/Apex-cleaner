---
paths: ["app/src/main/java/**/ui/tv/**/*.kt"]
---
# Android TV rules
- Use `androidx.tv.material3.*` primitives for every TV layout.
- Focus: 1.05x scale and 3dp #7C3AED border; `focusRestorer()` on list containers; initial focus via FocusRequester.
- Tab and screen switches are instant (no transition).
