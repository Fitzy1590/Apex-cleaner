---
paths: ["app/src/main/java/**/net/mitm/**/*.kt"]
---
# Web page cleaner (HTTPS) rules. Owner voted 2026-11-26: browsers only, off by default.
- The CA private key is made inside Android KeyStore and never leaves it (it only signs). Only the public certificate is written to disk.
- Only Chrome-family browsers and Samsung Internet (MitmEngine.trustingBrowsers), Android 10+, only while the switch is on AND the certificate is installed. Never other apps, never Firefox.
- `MitmEngine.neverOpen` always wins: banking/payment/essential/DRM lists, the user's Allow list, government, and sign-in/payment-style names pass through untouched.
- The real site is always verified with the system trust store and a hostname check; a bad certificate means the connection fails, never a silent downgrade.
- Inject CSS and script only into `text/html` replies that are uncompressed, 2xx and under 3 MB; everything else passes byte for byte. The page's Content-Security-Policy is only widened by our own one-time nonce.
- Never log or store decrypted content. Live Traffic never records decrypted bytes.
- QUIC (UDP 443) from those browsers is refused only while the cleaner is active, so they fall back to the cleanable kind.
- Keep CertAuthority, Sni, HttpDirection and CosmeticRules pure and covered by the JVM tests (MitmSessionTest runs a full local TLS round trip).
