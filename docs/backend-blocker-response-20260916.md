# Backend Response to the Frontend Blocker List (B1–B5)

**Date:** 2026-09-16 (updated same day — see status banner)
**Author:** l0sgAi (backend)
**Code reviewed:** branch `feature/login-redirect-20260915`, originally HEAD `206f72c`
("feat(auth): Implement OAuth callback redirection to the frontend page")

> **Status banner (2026-09-16, second revision).** B1, B2 and B4 are now **fully done** on
> `feature/login-redirect-20260915`, including the two decisions that were open in the first
> revision of this document:
>
> | Commit | Contents |
> |---|---|
> | `206f72c` | B1 (302 redirect flow) + B4 (`/auth/me` with `nickname`/`avatarUrl`) |
> | `97bb063` | B2: contract `blocbuyToken` → `apiKey in: cookie`; `/auth/me` + `/auth/logout` carry a `security` requirement (Q3 folded in); `contracts/openapi.json` regenerated in the same commit |
> | `d647da1` | B1 landing path settled: success 302 → `${FRONTEND_URL}/auth/callback` on every profile; failure target unchanged |
> | `f1962d0` | this document |
>
> Verified end-to-end before committing: `./mvnw verify -Popenapi-export` (JDK 21) — BUILD SUCCESS,
> all unit tests and Testcontainers ITs green (`AuthControllerTest` 3/3, `AdminListingFlowIT` 23/23
> with the new contract assertions). The regenerated contract diff contains **exactly** the three
> expected changes and nothing else.
>
> **What remains:** B3 (held on Q4), B5's console checks (Q7/Q8), the unowned CSP/HSTS work (Q6).
> The frontend integration walkthrough is in [Section 8](#8-frontend-integration-flow-after-the-merge).

---

## 1. Summary

| ID | Ask | Status | Blocked on |
|---|---|---|---|
| B1 | OAuth callback 302s to the frontend instead of returning JSON | **Done** — landing path decided: `/auth/callback` | Nothing. Frontend may still veto the path; it is a one-line yml change per profile |
| B2 | Contract: `blocbuyToken` → `apiKey in: cookie` | **Done** (commit `97bb063`), including the Q3 scope | The merge PR needs the `contract-change-approved` label |
| B3 | `application-ec2.yml`: `sa-token.cookie.secure: true` | **Agreed, held** | Confirmation that dev is reachable *only* through CloudFront (Q4) |
| B4 | `GET /auth/me` returns `nickname` + `avatarUrl` | **Done** (commit `206f72c`) | Nothing |
| B5 | Confirm `GOOGLE_REDIRECT_URI` for dev | **Value confirmed in repo**, two external checks open | AWS/Google console access (l0sgAi or PPsyrius) |

Nothing required a design change or a new endpoint. Critical path now: **merge
`feature/login-redirect-20260915` → frontend integrates per Section 8** → console checks in one
pass → flip B3 once Q4 confirms.

---

## 2. B1 — OAuth callback: 302 to the frontend, no token in the body

### Status: done, including the landing-path decision

`AuthController.googleCallback` (`backend/blocbuy-adapter/src/main/java/com/blocbuy/identity/web/AuthController.java:100-112`):

```java
StpUtil.login(result.userId());                        // line 110
return redirect(authWebProperties.successRedirectUri()); // line 111
```

- The response is `302 FOUND` with a `Location` header and **no body at all** (`ResponseEntity<Void>`).
- The session token is never in a body, a URL, or a log. It rides only the Sa-Token HttpOnly
  cookie set on `StpUtil.login`.
- Failures also 302 — to `blocbuy.frontend.failure-redirect-uri`, with the `{errorCode}`
  placeholder replaced by the business error code (line 108), so the user never lands on a raw
  JSON error page.
- Covered by `AuthControllerTest`: `callbackSuccessOpensSessionAndRedirectsToFrontend`,
  `callbackFailureRedirectsToFrontendWithErrorCode`, `meWithoutSessionIsRejectedByTheInterceptor`.

### Landing path — decided (commit `d647da1`)

Backend proceeded with its preferred option rather than holding the branch:

```yaml
blocbuy:
  frontend:
    success-redirect-uri: "${FRONTEND_URL:}/auth/callback"                     # ec2, docker
    success-redirect-uri: "${FRONTEND_URL:http://localhost:5173}/auth/callback" # local
    failure-redirect-uri: "${FRONTEND_URL:}/login?error={errorCode}"           # unchanged, all profiles
```

- **Absolute `Location` on every profile** — one config shape, dev and local behave identically.
  On dev the origin is CloudFront (same-site from the browser's perspective); local is genuinely
  cross-origin (`:8589` → `:5173`) so it must be absolute anyway.
- If the frontend wants a different path or a path-only `Location`, it is a one-line change per
  profile file — say so and we flip it; it does not need to gate anything.

---

## 3. B2 — Contract: `blocbuyToken` security scheme → `apiKey in: cookie`

### Status: done (commit `97bb063`)

- `AdminListingController.java:52-57`: `in = SecuritySchemeIn.HEADER` → `SecuritySchemeIn.COOKIE`.
  `paramName = "blocbuy-token"` already matched the cookie name, so only `in` changed.
- `contracts/openapi.json` regenerated via `./mvnw verify -Popenapi-export` and committed in the
  same commit (CI fails on contract drift). Note: the repo has **no Makefile** — `make
  contract-check` was a stale comment, now corrected; the real command is the Maven one above and
  in `backend/README.md:50`.
- **Descriptive only — no runtime behaviour changed.** Sa-Token reads the token from the header
  *and* the cookie by default; the browser flow has always used the cookie.

### Q3 scope — folded in, as agreed

`/api/v1/auth/me` and `/api/v1/auth/logout` now carry a **method-level**
`@SecurityRequirement(name = "blocbuyToken")` on `AuthController`. Method-level, deliberately not
class-level: the `/oauth2/google/**` endpoints on the same controller are public paths
(`SaTokenConfig.PUBLIC_PATHS`) and a class-level annotation would have marked the login handshake
itself as authenticated. `AdminListingFlowIT.openApiDocumentsAdministratorTokenWithoutProtectingPublicBrowse`
now asserts all of: scheme `in: cookie`, security on the two session endpoints, **no** security on
the two OAuth handshake endpoints, no top-level `security`, and public product browse untouched.

What the generated client now sees (verified in the regenerated contract):

```json
"blocbuyToken": { "type": "apiKey", "name": "blocbuy-token", "in": "cookie" }
"/api/v1/auth/me":     { "get":  { "security": [ { "blocbuyToken": [] } ] } }
"/api/v1/auth/logout": { "post": { "security": [ { "blocbuyToken": [] } ] } }
```

### PR mechanics at merge time

`ci-backend.yml:145-160` runs `openapi-diff --fail-on-incompatible` against the base branch.
Changing a security scheme is a backward-incompatible diff, so the merge PR **does** need the
`contract-change-approved` label. The regenerated contract is already committed, so the drift
check (`ci-backend.yml:141-143`) will pass as-is.

For reference, the interceptor's actual public paths are in `SaTokenConfig.java`:
`/api/v1/auth/oauth2/**`, `/api/v1/webhooks/**`, the springdoc paths, `/actuator/health/**`,
`/actuator/prometheus`, plus anonymous `GET /api/v1/products/**`. Everything else under
`/api/v1/**` requires a session — paths beyond the two auth endpoints still carry no `security`
entry in the contract (out of scope for this PR; can be revisited if the frontend wants the whole
surface annotated).

---

## 4. B3 — `sa-token.cookie.secure: true` on EC2

### Status: agreed in principle, held pending one confirmation (unchanged)

The base profile already has the hardened settings (`application.yml:36-38`): `http-only: true`,
`secure: true`, `same-site: Lax`. The **only** thing turning it off on dev is the override at
`application-ec2.yml:20`, whose comment says TLS termination is pending.

Backend will flip it the moment someone confirms both:

1. The dev CloudFront distribution is live and serving.
2. The dev EC2 security group no longer accepts `:8589` from `0.0.0.0/0` — i.e. nobody is testing
   against the raw IP any more. (If anyone still reaches dev over plain HTTP, browsers silently
   drop a `Secure` cookie and **every login on dev breaks with no error message**.)

`application-docker.yml` and `application-local.yml` stay `secure: false`; both are plain HTTP by
design and are not affected.

### Related dev-host checks while someone is in there

- `SERVER_FORWARD_HEADERS_STRATEGY=framework` in `/etc/blocbuy/backend.env` (Q5) — the
  CloudFront→ALB origin leg is plain HTTP:80, so the app needs forwarded-header handling for
  anything it derives from the request scheme.
- **CSP/HSTS are not set anywhere today** — no CloudFront `ResponseHeadersPolicy`, no Spring header
  configuration. They belong on the CloudFront distribution, not in this repo. That work is
  unowned (Q6) — it needs an owner if it is a Sprint 2 gate.

---

## 5. B4 — `GET /auth/me` returns `nickname` and `avatarUrl`

### Status: done (commit `206f72c`)

`AuthController.me()` returns `MeResponse(userId, nickname, avatarUrl)`, sourced from
`UserAppService.CurrentUser`. The generated contract is in sync — `components.schemas.MeResponse`
carries all three fields. With the Q3 change folded in, the operation is now also typed as
authenticated in the contract.

Two notes for the header implementation:

- There is **no `LoginResponse` type anywhere in the backend.** We assume the blocker referred to
  the old JSON login body, which B1 removed. If it meant something else, tell us.
- `nickname` and `avatarUrl` are explicitly **nullable**. They are populated from the Google
  profile at registration, so accounts created before that was wired can have either as `null`.
  Render a fallback (initials avatar, userId-derived display name) rather than assuming both are
  present.

---

## 6. B5 — `GOOGLE_REDIRECT_URI` for dev

### Status: the value is pinned in the repo; two checks need console access (unchanged)

`deployment/backend/backend.env.example` carries the intended dev values:

```
GOOGLE_REDIRECT_URI=https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google/callback
GOOGLE_CLIENT_ID=292561274302-h39ekhsmfu9m3a2sukev37npa4d2jde6.apps.googleusercontent.com
```

The **path is correct** from code: a single `/api` prefix, no servlet context-path, matching the
controller mapping. The one-time state binding cookie is scoped to `/api/v1/auth`
(`AuthController.java:79-81`) and resolves correctly with no context-path on dev. No action needed
on either.

Still needs the account holder:

1. The deployed `/etc/blocbuy/backend.env` on the dev EC2 host actually carries that
   `GOOGLE_REDIRECT_URI`.
2. The Google console lists the exact CloudFront callback URI under Authorized redirect URIs
   (exact string match — trailing slash or `http` vs `https` mismatch fails at Google with
   `redirect_uri_mismatch` and never reaches our code).
3. `FRONTEND_URL` on the same host — no default, `@NotBlank`-validated, **startup fails if unset**.
   Should be the CloudFront origin. Same env file as (1) — check both in one pass.

---

## 7. Open questions — status after this revision

| # | Question | Status |
|---|---|---|
| Q1 | Success landing path `/auth/callback`? Failure `/login?error={errorCode}`? | **Decided by backend** (Section 2). Frontend veto still possible — one-line yml change |
| Q2 | Absolute or path-only `Location` on dev? | **Decided**: absolute everywhere, one config shape |
| Q3 | Fold `security` on `/auth/me` + `/auth/logout` into the B2 PR? | **Done** (commit `97bb063`) |
| Q4 | Is dev CloudFront live, and is `:8589` closed to the public internet? | **Open** — l0sgAi / PPsyrius; gates B3 |
| Q5 | Is `SERVER_FORWARD_HEADERS_STRATEGY=framework` set on the dev host? | **Open** — l0sgAi / PPsyrius |
| Q6 | Who owns the CloudFront CSP/HSTS response-headers policy? | **Open — unassigned**; needed before it can be a Sprint 2 gate |
| Q7 | Does `/etc/blocbuy/backend.env` on dev carry the right `GOOGLE_REDIRECT_URI` **and** `FRONTEND_URL`? | **Open** — l0sgAi / PPsyrius |
| Q8 | Does the Google console list the CloudFront callback URI? | **Open** — l0sgAi / PPsyrius |

---

## 8. Frontend integration flow (after the merge)

Backend merges `feature/login-redirect-20260915` into `dev` (with the `contract-change-approved`
label on the PR). Once it lands, integrate in this order:

1. **Regenerate the client** from `contracts/openapi.json` on `dev`. You get: `MeResponse` with
   `userId`/`nickname`/`avatarUrl`, the `blocbuyToken` cookie scheme, and `/auth/me` +
   `/auth/logout` typed as authenticated.

2. **Login start** — navigate the browser (top-level navigation, **not** XHR/fetch) to
   `GET /api/v1/auth/oauth2/google`. The backend 302s to Google; after consent, Google returns to
   the backend callback, which opens the session (HttpOnly `blocbuy-token` cookie) and 302s to
   `<origin>/auth/callback`. The token never appears in any URL or body — do not look for one.

3. **Add the `/auth/callback` route.** It receives **no parameters and no token** — the session
   cookie was already set by the callback's `Set-Cookie` before the redirect. On mount, call
   `GET /api/v1/auth/me`; on success populate the session store and route on (e.g. `/account` or
   the original target). A 401 here means the flow failed mid-way — send the user to `/login`.

4. **Login failure page** — the backend failure redirect is `/login?error={errorCode}`. Read the
   `error` query param and map the code to a user-facing message (e.g. `IDENTITY_401_001` =
   state/callback mismatch — "please try logging in again").

5. **Header user area** — render from the `/auth/me` response. `nickname` and `avatarUrl` are
   nullable (pre-existing accounts): fall back to an initials avatar / derived display name.

6. **Logout** — `POST /api/v1/auth/logout` (cookie rides automatically), then clear client-side
   session state and route to `/login`. Do not try to delete the cookie from JS — it is HttpOnly;
   the endpoint invalidates the server-side session.

7. **HTTP client configuration** — the session is a cookie, so requests need no `Authorization`
   header, but cross-origin fetches must send credentials:
   - **dev/prod:** frontend and API are one CloudFront origin (`/*` → S3, `/api/*` → ALB) —
     same-origin, nothing to configure.
   - **local:** the backend has **no CORS configuration at all**, and `SameSite=Lax` cookies do
     not ride cross-origin XHR. Use the Vite dev-server proxy (`/api` → `http://localhost:8589`)
     so the browser sees a same-origin API — this is the supported local setup. Direct
     `:5173 → :8589` fetches will fail regardless of `credentials: 'include'`.

8. **Nothing to store.** No token in `localStorage`, no interceptor attaching headers. The only
   client-side session state is the `/auth/me` response and its 401.

---

## 9. Remaining backend/infrastructure work (not gating the frontend)

1. **Merge** `feature/login-redirect-20260915` → `dev`, label `contract-change-approved` (l0sgAi).
2. **Q4/Q5/Q7/Q8** in a single console pass (l0sgAi / PPsyrius): security group `:8589`,
   `SERVER_FORWARD_HEADERS_STRATEGY`, `GOOGLE_REDIRECT_URI` + `FRONTEND_URL` in
   `/etc/blocbuy/backend.env`, Google console redirect URI list.
3. **B3 flip** (`application-ec2.yml:20` → `secure: true`) the moment Q4 confirms — one line,
   its own tiny PR.
4. **Q6** — assign an owner for the CloudFront CSP/HSTS response-headers policy if it is a
   Sprint 2 gate.

---

## 10. Scope note

This document answers the **backend** section of the blocker list only. The `frontend/` directory
in this repository contains a README and nothing else, so no frontend-side claim here is based on
code. The "AWS asks" section of the original list was truncated in the copy we received — send it
over and we will do the same pass on it.
