# Backend Response to the Frontend Blocker List (B1–B5)

**Date:** 2026-09-16
**Author:** l0sgAi (backend)
**Code reviewed:** branch `feature/login-redirect-20260915`, HEAD `206f72c`
("feat(auth): Implement OAuth callback redirection to the frontend page")

> **Read this first.** Two of the five asks (B1 and B4) are already implemented on
> `feature/login-redirect-20260915` and are not yet on `dev`. The blocker list appears to have
> been written against `dev`. Once that branch merges, the frontend work those two gate can start
> immediately. The remaining three are small, but two of them need an answer from the frontend or
> the AWS account holder before backend can move — those questions are collected in
> [Section 7](#7-open-questions--who-must-answer).

---

## 1. Summary

| ID | Ask | Status | Blocked on |
|---|---|---|---|
| B1 | OAuth callback 302s to the frontend instead of returning JSON | **Done**, one decision open | FE: confirm the landing path and absolute-vs-relative `Location` |
| B2 | Contract: `blocbuyToken` → `apiKey in: cookie` | **Agreed, not started** | Nothing — backend will do it; needs the `contract-change-approved` label on the PR |
| B3 | `application-ec2.yml`: `sa-token.cookie.secure: true` | **Agreed, held** | Confirmation that dev is reachable *only* through CloudFront |
| B4 | `GET /auth/me` returns `nickname` + `avatarUrl` | **Done** | Nothing — regenerate the client from the current contract |
| B5 | Confirm `GOOGLE_REDIRECT_URI` for dev | **Value confirmed in repo**, two external checks open | AWS/Google console access (l0sgAi or PPsyrius) |

Nothing on this list requires a design change or a new endpoint. The critical path is: merge
`feature/login-redirect-20260915` → settle the B1 landing path → one small PR covering B2 (+ B3 if
confirmed).

---

## 2. B1 — OAuth callback: 302 to the frontend, no token in the body

### Status: implemented; one decision left for the frontend

`AuthController.googleCallback` (`backend/blocbuy-adapter/src/main/java/com/blocbuy/identity/web/AuthController.java:100-112`) already does exactly what the ask describes:

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
- Covered by tests in `backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/AuthControllerTest.java`:
  `callbackSuccessOpensSessionAndRedirectsToFrontend`, `callbackFailureRedirectsToFrontendWithErrorCode`,
  `meWithoutSessionIsRejectedByTheInterceptor`.

### What is *not* done

The landing path is `${FRONTEND_URL}/`, not `/auth/callback`. That is configuration, not code —
one line per profile in `application-ec2.yml`, `application-docker.yml`, `application-local.yml`:

```yaml
blocbuy:
  frontend:
    success-redirect-uri: "${FRONTEND_URL:}/"          # today
    success-redirect-uri: "${FRONTEND_URL:}/auth/callback"  # if you confirm
```

### On "relative"

A relative `Location` is technically fine — `AuthWebProperties` only validates `@NotBlank`, and
Spring emits whatever URI we hand it — but it cannot be relative on **every** profile:

- **local**: genuinely cross-origin (backend `:8589`, Vite `:5173`). Must stay absolute.
- **dev / prod**: everything is one CloudFront origin (`/*` → S3, `/api/*` → ALB), so a bare
  `/auth/callback` resolves correctly, and so does the absolute CloudFront URL.

Backend's preference is to keep one config shape — `${FRONTEND_URL}/auth/callback` — so dev and
local behave identically and there is no profile-specific special case. If the frontend has a
concrete reason to need a path-only `Location` on dev, say so and we will drop the origin on the
non-local profiles.

### Needed from the frontend

1. Confirm the success landing path: `/auth/callback`?
2. Confirm the failure target stays `/login?error={errorCode}`, or name a different one.
3. Absolute (`https://<cloudfront>/auth/callback`) or path-only (`/auth/callback`) on dev?

---

## 3. B2 — Contract: `blocbuyToken` security scheme → `apiKey in: cookie`

### Status: agreed, will be done in one PR

The contract is **generated**, so `contracts/openapi.json` is not the file to edit. The source of
truth is the annotation in
`backend/blocbuy-adapter/src/main/java/com/blocbuy/catalog/web/AdminListingController.java:52-57`:

```java
@SecurityScheme(
        name = "blocbuyToken",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,   // → SecuritySchemeIn.COOKIE
        paramName = "blocbuy-token")
```

`paramName` already matches `sa-token.token-name: blocbuy-token` (`application.yml:27`), which is
also the cookie name — so only `in` changes. Backend will change the annotation, run
`make contract-check` to regenerate, and commit the regenerated `contracts/openapi.json`.

**This is descriptive only — no runtime behaviour changes.** Sa-Token reads the token from the
header *and* the cookie by default; the browser flow has always used the cookie. The contract was
simply describing the wrong one.

### PR mechanics

`ci-backend.yml:145-160` runs `openapi-diff --fail-on-incompatible` against the base branch. Changing
a security scheme is a backward-incompatible diff, so the PR **does** need the
`contract-change-approved` label, as the blocker list says. `ci-backend.yml:141-143` additionally fails if
the committed contract drifts from the generated one, so the regenerated JSON must be in the same commit.

`backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/AdminListingFlowIT.java:479` and `:496`
assert the scheme shape and will be updated in the same PR.

### One thing the frontend should decide now, because it is the same PR and the same label

`@SecurityRequirement(name = "blocbuyToken")` currently sits **only** on `AdminListingController`.
The top-level `security` in the contract is `[]`, and `/api/v1/auth/me`, `/api/v1/auth/logout` and
every other path behind the Sa-Token interceptor carry **no** `security` entry at all.

So if the gate is "the generated client types this call as authenticated", changing `in: cookie`
alone will not get you there — the generated client still sees those operations as anonymous.
Fixing it means either a global `security` requirement or annotating the auth endpoints. It is the
same PR, the same label, and roughly the same size. **Do you want it folded in?**

For reference, the interceptor's actual public paths are in
`backend/blocbuy-infrastructure/src/main/java/com/blocbuy/common/config/SaTokenConfig.java`:
`/api/v1/auth/oauth2/**`, `/api/v1/webhooks/**`, the springdoc paths, `/actuator/health/**`,
`/actuator/prometheus`, plus anonymous `GET /api/v1/products/**`. Everything else under
`/api/v1/**` requires a session.

---

## 4. B3 — `sa-token.cookie.secure: true` on EC2

### Status: agreed in principle, held pending one confirmation

The diagnosis is correct. The base profile already has the hardened settings
(`application.yml:36-38`): `http-only: true`, `secure: true`, `same-site: Lax`. The **only** thing
turning it off on dev is the override at `application-ec2.yml:20`, whose comment says TLS
termination is pending.

### Why backend has not flipped it yet

`docs/deploy/dev-cloudfront-deployment.md` still carries **`Status: to be executed`** at the top.
The repo therefore cannot tell us whether that comment is stale or accurate. The risk is concrete
and one-directional: if anyone still reaches dev at `http://<EC2-public-IP>:8589`, browsers will
silently drop a `Secure` cookie over plain HTTP and **every login on dev breaks with no error
message**.

Backend will flip it the moment someone confirms both:

1. The dev CloudFront distribution is live and serving.
2. The dev EC2 security group no longer accepts `:8589` from `0.0.0.0/0` — i.e. nobody is testing
   against the raw IP any more.

`application-docker.yml` and `application-local.yml` stay `secure: false`; both are plain HTTP by
design and are not affected.

### A related dev-host check while someone is in there

The CloudFront→ALB origin leg is plain HTTP:80 (`infra/terraform/backend-app-stack/main.tf:19-22`).
That is fine for a `Secure` cookie — the browser only ever sees the HTTPS CloudFront leg — but the
app needs forwarded-header handling for anything it derives from the request scheme. Per
`docs/deploy/dev-cloudfront-deployment.md:285` that means `SERVER_FORWARD_HEADERS_STRATEGY=framework`
together with the `AllViewerExceptHostHeader` origin request policy. Please confirm the env var is
actually set in `/etc/blocbuy/backend.env` on the dev host.

### On the CSP/HSTS gate

To set expectations: **nothing in this repository sets CSP or HSTS today.** There is no
`ResponseHeadersPolicy` in the CloudFront deployment doc and no Spring header configuration. Both
headers belong on the CloudFront distribution, not in the backend application, so flipping
`secure: true` does not deliver them and is not blocked by them either. That work is currently
unowned — it needs an owner if it is a Sprint 2 gate.

---

## 5. B4 — `GET /auth/me` returns `nickname` and `avatarUrl`

### Status: already done — no backend change needed

Same commit as B1. `AuthController.me()` (`AuthController.java:121`) returns
`MeResponse(userId, nickname, avatarUrl)` (`AuthController.java:137`), sourced from
`UserAppService.CurrentUser` (`backend/blocbuy-application/.../identity/service/UserAppService.java:27`).

The generated contract is already in sync — `components.schemas.MeResponse` in
`contracts/openapi.json` carries all three fields. **Regenerate the client from the current
contract on this branch and the typing is there.**

### Two notes for the header implementation

- There is **no `LoginResponse` type anywhere in the backend.** We assume the blocker referred to
  the old JSON login body, which B1 removed. If it meant something else, tell us.
- `nickname` and `avatarUrl` are explicitly documented as nullable (`UserAppService.java:24-25`).
  They are populated from the Google profile at registration, so accounts created before that was
  wired can have either as `null`. Please render a fallback (initials avatar, userId-derived
  display name) rather than assuming both are present.

---

## 6. B5 — `GOOGLE_REDIRECT_URI` for dev

### Status: the value is pinned in the repo; two checks need console access

`deployment/backend/backend.env.example` already carries the intended dev values:

```
GOOGLE_REDIRECT_URI=https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google/callback
GOOGLE_CLIENT_ID=292561274302-h39ekhsmfu9m3a2sukev37npa4d2jde6.apps.googleusercontent.com
```

The **path is correct** and we can confirm it from code: a single `/api` prefix, no servlet
context-path (`application.yml:47-49` is explicit about this), matching the controller mapping
`ApiConstants.API_V1 + "/auth"` plus `@GetMapping("/oauth2/google/callback")`.

Related and already handled: the one-time state binding cookie is scoped to
`request.getContextPath() + /api/v1/auth` (`AuthController.java:79-81`). With no servlet
context-path on dev that resolves to `/api/v1/auth`, so the cookie is sent back on the callback.
No action needed.

### What the repo cannot verify — needs the account holder

1. **The deployed value.** `backend.env.example` is a template, not the deployed file. Someone with
   SSH/SSM access must confirm `/etc/blocbuy/backend.env` on the dev EC2 host actually carries that
   `GOOGLE_REDIRECT_URI`.
2. **The Google console.** Confirm `https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google/callback`
   is listed under Authorized redirect URIs for client `292561274302-...`. An exact string mismatch
   (trailing slash, `http` vs `https`) produces a `redirect_uri_mismatch` at Google and never
   reaches our code.
3. **`FRONTEND_URL` on the same host.** It has no default (`${FRONTEND_URL:}`) and
   `AuthWebProperties` is `@NotBlank`-validated, so the application **fails at startup** if it is
   unset. It should be the CloudFront origin. This is the same env file as (1) — check both in one
   pass.

---

## 7. Open questions — who must answer

| # | Question | Blocks | Owner |
|---|---|---|---|
| Q1 | Success landing path — `/auth/callback`? Failure stays `/login?error={errorCode}`? | B1 | Frontend |
| Q2 | Absolute or path-only `Location` on dev? | B1 | Frontend |
| Q3 | Fold the missing `security` requirement on `/auth/me` + `/auth/logout` into the B2 PR? | B2 | Frontend |
| Q4 | Is the dev CloudFront distribution live, and is `:8589` closed to the public internet? | B3 | l0sgAi / PPsyrius |
| Q5 | Is `SERVER_FORWARD_HEADERS_STRATEGY=framework` set on the dev host? | B3 (soft) | l0sgAi / PPsyrius |
| Q6 | Who owns the CloudFront CSP/HSTS response-headers policy? | B3 gate | Unassigned |
| Q7 | Does `/etc/blocbuy/backend.env` on dev carry the right `GOOGLE_REDIRECT_URI` **and** `FRONTEND_URL`? | B5 | l0sgAi / PPsyrius |
| Q8 | Does the Google console list the CloudFront callback URI? | B5 | l0sgAi / PPsyrius |

---

## 8. Proposed sequence

1. **Merge `feature/login-redirect-20260915` into `dev`.** This alone closes B4 outright and closes
   B1 apart from the landing-path decision. Frontend can begin the `/account` route and header user
   area against it.
2. **Answer Q1–Q3** (frontend, minutes). Q1/Q2 turn into a one-line config change per profile.
3. **One backend PR** carrying B2 (+ the Q3 scope if wanted) + B1's config change + B3 if Q4 has
   come back positive. Label it `contract-change-approved`.
4. **Q4/Q5/Q7/Q8** in a single pass on the AWS/Google consoles by the account holder.
5. **Q6** needs an owner assigned before it can be called a Sprint 2 gate.

---

## 9. Scope note

This document answers the **backend** section of the blocker list only. The `frontend/` directory in
this repository contains a README and nothing else, so no frontend-side claim here is based on
code. The "AWS asks" section of the original list was truncated in the copy we received — send it
over and we will do the same pass on it.
