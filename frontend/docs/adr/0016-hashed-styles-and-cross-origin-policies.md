# 0016. Hashed UI styles and progressive cross-origin policies

Date: 2026-10-27

## Status

Accepted (#637–#640); source preparation, not deployment acceptance.

## Context

BlocBuy is a pure CSR app with one promoted artifact (ADRs 0001 and 0004). HeroUI's React Aria dependency injects static press/overscroll styles, while application components set dynamic CSSOM properties. Removing all inline-style permission without accommodating the injected sheets breaks interaction styling. External product and Google-avatar images do not consistently supply CORS/CORP permission. The app has no feature requiring cross-origin isolation.

## Decision

Authorize only the two reviewed React Aria stylesheet SHA-256 hashes in the canonical CSP, remove `unsafe-inline`, and set `style-src-attr 'none'`. The non-permissive `report-sample` token enables local violation diagnostics; no remote reporting endpoint is configured. Do not authorize arbitrary inline style blocks or attributes. JavaScript setting individual CSSOM properties remains permitted by CSP, preserving progress bars and overlay positioning without authorizing HTML-injected styles.

Run `scripts/verify-csp-styles.mjs` during both build and check. Resolve the React Aria dependency actually used by HeroUI, inspect its two known injections without evaluating dependency code, and fail on missing/changed styles, unresolved interpolation or unreviewed hashes. Dependency upgrades require reviewing new stylesheet content and rerunning browser tests, not automatically approving new hashes.

Use COEP `credentialless`, COOP `same-origin` and CORP `same-origin` from the shared policy. The user approved progressive COEP after browser testing: Chromium/Firefox must enforce credentialless isolation and cross-origin no-CORS cookie omission; tested WebKit keeps baseline compatibility without an isolation/cookie-omission guarantee. Do not skip its image, styling, opener, resource or navigation tests. No application feature may assume `crossOriginIsolated` is always true. The [deployment runbook](../../../docs/deploy/dev-cloudfront-deployment.md#remaining-browser-policy-candidate--2026-10-27) owns resource classification and rollout prerequisites.

Alternatives rejected: retaining unrestricted inline styles (does not address #637); a reusable static nonce (not request-specific authorization); patching vendor injection code (unnecessary fork/upgrade burden); `require-corp` alone (observably blocks required images); claiming universal isolation (false for credentialless in tested WebKit). Universal isolation would require separately reviewed external-image permission/hosting changes.

## Consequences

Hash drift is a deliberate build failure. Existing artifacts and edge policies must remain compatible during any future hash update: temporarily authorize reviewed old/new hashes before publishing the new app, then remove obsolete hashes after rollout verification. These two approved hashes match the already deployed dependency version, so this candidate does not require changing its runtime injections.

The opt-in `E2E_SECURITY_BROWSERS=all` matrix extends ADR 0011's default Chromium journeys with Firefox, WebKit and touch-emulated WebKit. It does not establish physical iOS-device compatibility or real OAuth/Stripe acceptance. Vite preview, like localhost Caddy, omits only `upgrade-insecure-requests` for HTTP localhost; the production policy retains it.

WebKit reports a blocked browser-internal `text-overflow:inherit` style for a bare native SELECT, reproduced without app scripts. The browser fixture annotates only that exact sample on a SELECT with no page style attribute; all other unexpected styling violations still fail. The CSP does not authorize the native style or arbitrary inline attributes. Touch emulation checks sheet interaction and explicit acceptance of the source-verified overscroll hash, not execution of the vendor's native iOS-only injection on a physical device.

Same-origin credentials remain available to application requests. Cross-origin no-CORS images on supporting engines do not receive credentials; private images requiring cookies are not supported by this policy. External CDNs are not modified or granted permissions by a CORP header on the app's own origin. Findings remain open until authorized deployment and follow-up verification.

2026-10-27 review addendum: the guard now enforces the exact reviewed style sources, hashes and attribute denial, rejecting extra/duplicate/overriding style directives; its vendor inspection remains a targeted check of two known injections, not a comprehensive dependency-source audit. Cookie-omission evidence must include a real server-observed credentialed baseline on a cross-origin, same-site image, followed by the otherwise identical credentialless document; intercepted cookie absence alone does not establish COEP enforcement. WebKit may gain isolation support: record either outcome, retain all compatibility assertions, and require image-cookie omission whenever isolation is active. Versioned [Docker tooling](../../tooling/README.md) and a supplemental manual CI workflow reproduce the all-engine suite without changing the default Chromium PR gate. The policy's compatibility/maintenance costs remain deliberate despite the absence of an application feature requiring isolation; real integrations and physical iOS acceptance are still pending.
