# B3 Secure cookies on the `ec2` profile — implementation plan

Date: 2026-10-02 · Branch: `feature/secure-cookie-20261002` · Issue: #96 (B3, Sprint 2 container #93, carried into Sprint 3) · Owner: los gai · Gates: T2-11 (CSP/HSTS), [frontend ADR 0002](../../../frontend/docs/adr/0002-same-origin-api-and-cookie-auth.md) consequences · Related: [Liu Jin task plan §3.5](liu-jin-task-plan.md), [backend blocker response §4](../../backend-blocker-response-20260916.md), [NFR-01](../../quality/nfr-register.md)

> **Status (2026-10-02): D1–D2 implemented on this branch; V1 and V2 passed locally (full `verify`: 1,508 tests, zero failures). V3/V4 wait for the dev backend deploy.**

## 1. Goal and acceptance criteria

Browsers reach the dev backend only through CloudFront over HTTPS, so both auth cookies must carry `Secure` on the `ec2` profile. `local` and `docker` stay plain HTTP.

| # | Criterion (issue #96) | Delivered by |
| --- | --- | --- |
| AC1 | `curl -sSI https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google \| grep -i set-cookie` shows `Secure` on `blocbuy-oauth-state` | §3 config change; §4 V3 after deploy |
| AC2 | After a Google login on dev, `blocbuy-token` shows `Secure` in DevTools | §3 config change; §4 V4 after deploy |

## 2. Baseline (read on 2026-10-02 from `dev` 8ee2525 and the public dev edge)

| Piece | State | Consequence |
| --- | --- | --- |
| `application.yml` | `sa-token.cookie.secure: true`, `blocbuy.frontend.state-cookie-secure: true` | The hardened values already exist; only profile overrides turn them off. |
| `application-ec2.yml` | Overrides both flags to `false`; comment says TLS termination is pending | Stale since the CloudFront → ALB → EC2 entry went live on 2026-09-06. |
| `application-local.yml`, `application-docker.yml` | Both flags `false` (plain HTTP) | Unchanged by this issue. |
| Dev edge | `curl -sSI https://…/api/v1/auth/oauth2/google` → `HTTP/2 302`, `set-cookie: blocbuy-oauth-state=…; Path=/api/v1/auth; HttpOnly; SameSite=Lax` (no `Secure`); `http://…` → `301` from CloudFront | Viewer protocol is `redirect-to-https` (`infra/terraform/frontend-stack/cloudfront.tf`); no browser request reaches the backend over HTTP. |
| CloudFront → ALB | Plain HTTP (NFR-37, unmet) | Does not affect `Secure`: the browser only sees the HTTPS viewer connection. Origin TLS stays NFR-37. |
| Frontend dev server | Vite proxies `/api` to `http://localhost:8589` (local profile) | Local developers never receive `ec2` cookies; no local login regression. |
| Prod | `deployment/backend/compose.yml` runs prod with `SPRING_PROFILES_ACTIVE: ec2` too; prod edge not yet applied (#48) | Prod inherits `Secure`. Any prod entry must be HTTPS at the browser edge, which #48 already requires. |

## 3. Decisions and change

| ID | Decision | Recommendation | Alternative |
| --- | --- | --- | --- |
| D1 | How `ec2` gets `Secure` | **Delete both overrides from `application-ec2.yml`** so it inherits the base `true`. One source of truth; the profile file only lists real differences from the base. | Write `secure: true` explicitly (duplicates the base; a later base change could drift). |
| D2 | Guard against regression | New `CookieSecurityProfileTest`: loads `application.yml` plus each profile file through Spring Boot config data and asserts both flags — `true` on `ec2`, `false` on `local` and `docker`, and the two flags always equal. | Rely on the post-deploy curl only (catches regressions after they ship). |

Files:

- `backend/blocbuy-bootstrap/src/main/resources/application-ec2.yml`: remove `sa-token.cookie` and `blocbuy.frontend.state-cookie-secure` overrides.
- `backend/blocbuy-bootstrap/src/main/resources/application.yml`: comment now lists only local/docker as plain-HTTP overrides.
- `AuthWebProperties` Javadoc and `AuthController` comment: drop "ec2 until the CloudFront cutover".
- `backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/config/CookieSecurityProfileTest.java`: D2.
- `frontend/docs/adr/0002-same-origin-api-and-cookie-auth.md`: dated consequence paragraph (B3 merged).
- `docs/quality/nfr-register.md` NFR-01: evidence updated; status stays unmet until V3/V4 run on dev.
- `docs/agile/sprint-03/log/2026-10-02.md`: log entry.

No API surface change, so `contracts/openapi.json` is untouched.

## 4. Verification

| ID | Check | When |
| --- | --- | --- |
| V1 | `./mvnw -B -pl blocbuy-bootstrap -am test -Dtest=CookieSecurityProfileTest,AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false` | Local, before PR |
| V2 | Full Docker-backed `./mvnw -B verify` | Local or CI on the PR SHA |
| V3 | AC1 curl shows `Secure; HttpOnly; SameSite=Lax` on `blocbuy-oauth-state` | After the backend deploy to dev |
| V4 | Google login on dev completes; DevTools shows `blocbuy-token` with `Secure`, `HttpOnly`, `Lax`; then NFR-01 → `enforced` | After the backend deploy to dev |

## 5. Risks

- **Login breaks on dev if any browser path is HTTP.** Ruled out by the `redirect-to-https` viewer policy and the 301 probe; V4 confirms end to end.
- **The two flags drift apart.** D2 asserts they are equal on every profile.
- **Prod reuses `ec2`.** Safe only behind an HTTPS edge; recorded here and in the PR for #48/#226.
