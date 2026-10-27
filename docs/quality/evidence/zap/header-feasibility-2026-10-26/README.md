# Security-header feasibility check — 2026-10-26

Refs #610 and #636–#641; NFR-71. This is an investigation, not an implemented fix, owner-approved exception or post-remediation ZAP scan. No AWS configuration, application code or live data was changed. Repository source inspected: `6d14e449a3f738aee55ffb772ff81f07cb0261ac`; the deployed application revision was not independently established.

## Method and retained evidence

- [inventory.json](inventory.json): anonymous dev root headers, all 14 public on-sale listing details, and HEAD responses for all 42 distinct image URLs with `Origin` set to the dev SPA. This does not cover draft listings, historical order images or signed-in avatars.
- [audit.cjs](audit.cjs) and [audit.json](audit.json): six candidate/baseline policy modes across catalog, one product detail and login (18 navigations) in Chromium 156.0.8078.4. Candidate response headers were applied only inside an isolated local browser context, to navigation responses; CloudFront was not modified. GET/HEAD/OPTIONS traffic was permitted; mutating browser requests were blocked. No real login, checkout, invitation creation or other authenticated operation was performed.
- The browser recorded image-load results, CSP violations, failed requests, injected styles and JavaScript exceptions. The catalog probe appended an image from `https://example.com/blocbuy-controlled-csp-probe.png`; the restricted policy blocked it with an `img-src` violation. The baseline did not block it through CSP (Chromium subsequently rejected that response through ORB). The probe is separate from legitimate-image counts.

## Findings and next implementation work

| Issue | Observation | Recommended next step, not an approved disposition |
|---|---|---|
| #636 / Medium / `10055-4` | All 42 current public product image URLs use `dcsjrkmummxi7.cloudfront.net` and return 200. A candidate CSP allowlist preserved catalog/detail/login images while blocking the unapproved probe. | Replace the HTTPS-wide source with reviewed hosts, but verify Google avatar hosts and historical images first. The admin form currently accepts arbitrary HTTPS image URLs (`frontend/src/features/admin/listing-form.schema.ts`), so document/enforce supported hosts rather than silently accepting images the browser cannot display. |
| #637 / Medium / `10055-6` | Removing `unsafe-inline` blocked React Aria's injected press-handling stylesheet on all three pages. Authorizing exact stylesheet hashes removed the observed violations, with images and page loads preserved. React-generated progress-bar style attributes were present without attribute violations in these Chromium runs. | Pursue exact hashes for known injected styles instead of a broad inline allowance. Validate touch interactions, quote sheets, overlays, admin forms, progress bars and supported browsers before selecting this as the fix. A successful page load is not full functional compatibility evidence. |
| #641 / Low / `10063-1` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` produced no observed anonymous-page regression when combined with candidate COOP/CORP. Source uses `navigator.clipboard.writeText` and `navigator.share`, so blanket-denying all features would be inappropriate. Checkout is a full-page Stripe navigation, not an embedded Payment Request integration. | Deny confirmed-unused capabilities while preserving clipboard/share. Verify real supported interactions before deployment. |
| #639 / Low / `90004-3` | `COOP: same-origin` preserved the three anonymous pages. Google login and Stripe checkout use full-page navigation in source, not `window.open`. | Test actual sign-in/return and sandbox checkout/return in an approved session; the login landing page alone does not establish integration compatibility. |
| #640 / Low / `90004-1` | Candidate document `CORP: same-origin` did not break same-origin app loading. This does not establish appropriate sharing for every API/asset response or the separate image CDN. | Complete a resource-sharing matrix and negative cross-origin embedding checks. The existing CloudFront headers policy attaches to both SPA and `/api/*` behaviors. |
| #638 / Low / `90004-2` | `COEP: require-corp` blocked all 14 catalog product covers and the external product-gallery images. All 42 image HEAD responses lacked ACAO and CORP, including with an Origin request header. `COEP: credentialless` preserved images in Chromium and, with COOP, enabled `crossOriginIsolated`. | Do not deploy `require-corp` alone. Decide whether isolation is needed; then test credentialless across supported browsers and signed-in integrations, or evaluate a separately reviewed image-CDN CORP/CORS change. No need for SharedArrayBuffer/cross-origin isolation was found in application source. |

## Browser results

Counts are loaded images remaining in the DOM after decoding; the app removes failed external images and shows fallback content. The one image remaining under `require-corp` is the same-origin brand image, not a successfully loaded product image.

| Locally overridden document policy | Catalog images | Product images | Login images | CSP stylesheet violations |
|---|---:|---:|---:|---|
| Existing baseline | 15/15 | 4/4 | 4/4 | None |
| Image allowlist + Permissions-Policy + COOP + CORP | 15/15 | 4/4 | 4/4 | None |
| Above + `style-src 'self'` | 15/15 | 4/4 | 4/4 | One blocked inline stylesheet per page |
| Above strict style + exact React Aria stylesheet hashes | 15/15 | 4/4 | 4/4 | None observed |
| Allowlist/low-header candidate + COEP `require-corp` (original style allowance retained) | 1/1 remaining | 1/1 remaining | 4/4 | None; image requests blocked by COEP |
| Allowlist/low-header candidate + COEP `credentialless` (original style allowance retained) | 15/15 | 4/4 | 4/4 | None observed |

Every navigation returned 200 and no page JavaScript exceptions were recorded. There were no interactive, authenticated, mobile-device or non-Chromium checks. The hashed-style and credentialless candidates were tested separately, not as one combined policy. The candidate image list includes `lh3.googleusercontent.com` provisionally; no signed-in avatar source or compatibility is claimed.

React Aria's installed source injects the pressable stylesheet in `react-aria/dist/private/interactions/usePress.mjs`; `overlays/usePreventScroll.mjs` also contains an injected overscroll stylesheet for touch-scroll handling. The audit supplies SHA-256 hashes of these exact static strings. Only the pressable sheet was observed at runtime in these desktop pages; the overscroll hash is source-derived and not runtime-verified. Dependency upgrades can change these strings, so a maintained hash strategy needs regression coverage. A static reusable nonce would not be an appropriate security replacement.

## Repeat the browser experiment

To repeat using Docker, build the existing frontend build stage from the repository root and mount this script read-only. The output below goes to the approved host temporary directory, not the recorded evidence:

```powershell
docker build -f fullstack-localhost/frontend.Dockerfile --target build -t blocbuy-zap-frontend-check:local .
docker run --rm --ipc=host `
  --mount "type=bind,source=$PWD/docs/quality/evidence/zap/header-feasibility-2026-10-26/audit.cjs,target=/repo/audit.cjs,readonly" `
  --mount "type=bind,source=$env:LOCALAPPDATA/Temp/opencode,target=/evidence" `
  blocbuy-zap-frontend-check:local sh -c 'pnpm exec playwright install --with-deps --only-shell chromium && node /repo/audit.cjs /evidence/zap-policy-audit-rerun.json'
```

Write reruns to a new output path; do not overwrite the recorded evidence. The script uses the deployed dev app and locally overrides headers, so results can change when dev changes. This check does not replace a reviewed Terraform plan, authorized dev apply, real integration checks or a follow-up ZAP scan.
