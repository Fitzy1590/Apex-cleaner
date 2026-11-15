---
paths: ["app/src/main/java/**/core/**/*.kt"]
---
# Engine rules
- Every delete must pass `PathGuard`; add a test for any new deletable kind.
- Never touch shared_prefs, databases, tokens, logins, settings, user media/documents.
- No force-stop / killBackgroundProcesses. No restart loops.
- Skip any package where `ProtectedApps.isProtected` is true.
- Background work: WorkManager; deep work requires charging + idle; honor `PowerPolicy.isPaused` and screen-off slowdown.
- Keep the foreground service tiny: read-only checks, long intervals.
