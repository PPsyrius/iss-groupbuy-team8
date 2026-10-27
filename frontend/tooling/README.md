# Browser-security tooling

Build from the repository root; Docker is the only host prerequisite. The image contains the current SPA build, the lockfile-resolved Playwright engines and standard CA roots. It uses a digest-pinned Node base and exact pnpm version, not an unpublished local base image. OS package repositories still supply current packages; this is a versioned reproduction procedure, not a claim of bit-for-bit image reproducibility.

```powershell
docker build --file frontend/tooling/security-browsers.Dockerfile --tag blocbuy-security-browsers:review .
docker run --rm --cpus=2 --memory=6g blocbuy-security-browsers:review pnpm check
$evidence = Join-Path $env:LOCALAPPDATA "Temp/opencode/browser-security-new-capture"
New-Item -ItemType Directory -Path $evidence
docker run --rm --ipc=host --cpus=2 --memory=6g `
  --mount "type=bind,source=$evidence,target=/evidence" `
  blocbuy-security-browsers:review
```

The default command runs Chromium, Firefox, WebKit and touch-emulated WebKit against the built SPA and local fixtures. No real account or provider credentials are supplied. HTML and JSON reports, server-observed cookie-probe attachments and failure artifacts go to `/evidence`. Rebuild after source, dependency or policy changes; the image deliberately does not bind a possibly stale host `dist/` or `node_modules/`.

The supplemental [Browser security compatibility workflow](../../.github/workflows/security-browsers.yml) is manually dispatched on a chosen ref after the workflow is available on the default branch. It builds this recipe, runs `pnpm check` and the all-browser suite, and retains artifacts for 14 days even after browser failure. It has only `contents: read`, no environment secrets, no AWS access and no deployment step. It is not a required PR check or an automatically scheduled guarantee; run it for policy/vendor upgrades and before rollout acceptance. Hosted execution remains unverified until an authorized dispatch.

## What the tests establish

The credentialless probe uses real HTTP servers on different loopback ports: cross-origin but same-site. A server-set HttpOnly `SameSite=Strict` cookie must reach the image server from the baseline document before the otherwise identical candidate document is checked. Supporting browsers must omit all image cookies with COEP `credentialless`, while same-origin fetches retain the session cookie. This avoids false positives from SameSite restrictions, third-party-cookie blocking or incomplete intercepted request metadata. The probe verifies browser header semantics, not production authentication or CDN behavior.

Chromium/Firefox must report isolation. WebKit may report either value; compatibility always runs, support is recorded, and if it isolates then cookie omission is required too. A future WebKit improvement must not make the suite fail solely because isolation becomes available. Native iOS library injection, physical-device behavior and real OAuth/Stripe returns remain separate acceptance work.

The build/check stylesheet guard requires exactly the reviewed `style-src` sources/hashes and `style-src-attr 'none'`, rejecting extra sources/hashes, duplicate directives and `style-src-elem` overrides. It inspects two known vendor injections without executing dependency code. It is not a general scanner for arbitrary injection mechanisms throughout dependency source; dependency upgrades still need source review and browser testing. Reviewed old/new hash overlap during future rollout must be explicitly reflected in the guard as well as the edge policy, not automatically accepted.

## Optional anonymous public audit

This is separate from the workflow's local suite. The image also carries the [anonymous public audit](../../docs/quality/evidence/zap/remaining-policy-2026-10-27/audit.cjs) for explicitly requested read-only compatibility checks. It applies candidate document headers locally; it does not change deployed headers or prove remediation. Use a fresh output directory, never overwrite preserved captures, and never disable TLS verification.

```powershell
docker run --rm --ipc=host `
  --mount "type=bind,source=$evidence,target=/evidence" `
  blocbuy-security-browsers:review node /repo/audit.cjs `
  /repo/deployment/frontend/cloudfront/response-headers-policy.json /evidence/public-new-capture
```
