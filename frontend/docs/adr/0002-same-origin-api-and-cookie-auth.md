# 0002. Same-origin /api with cookie session and OAuth return

Date: 2026-09-16

## Status

Accepted

## Context

The dev distribution serves `/` from S3 and `/api/*` from the ALB (`CachingDisabled`, `AllViewerExceptHostHeader`,
all seven methods, `X-Origin-Secret` on the origin leg). The backend has no context path (`/api/v1` comes from
`ApiConstants.API_V1` on the controllers) and zero CORS configuration. Sessions are Sa-Token 1.46 with
`token-name: blocbuy-token`; `application.yml` sets the cookie `http-only: true`, `secure: true`, `same-site: Lax`,
but the `local`, `docker` and `ec2` profiles all override `secure: false`, and the `ec2` comment ("TLS termination
pending") is stale since the entry layer went live on 2026-09-06 (B3, #96). Login starts with
`GET /api/v1/auth/oauth2/google` (302 to Google plus a `blocbuy-oauth-state` cookie scoped to `/api/v1/auth`); the
callback `GET /api/v1/auth/oauth2/google/callback` still returns `Result<LoginResponse>` with the token in the body
(`AuthController.googleCallback`), which strands a top-level navigation on a JSON page. `GET /api/v1/auth/me`
returns `{loginId}`, logout is `POST /api/v1/auth/logout`, unauthenticated calls get a JSON 401 envelope,
`GET /api/v1/products/**` is anonymous, and the contract still declares `blocbuyToken` as `apiKey in: header` (B2, #95).

## Decision

The SPA calls `/api/*` on its own origin: the generated client's runtime config sets `baseUrl: '/'`
and `credentials: 'same-origin'`; locally Vite proxies `/api` to `http://localhost:8589` with the
prefix preserved. The session is the HttpOnly `blocbuy-token` cookie set by the backend; the
frontend never reads, stores or forwards a token. Login is a full-page navigation to
`/api/v1/auth/oauth2/google`; it completes when the backend answers the callback with a 302 to
`/auth/callback` (B1, #94; blueprint-review D1 option (a)), where the route invalidates `me`,
re-resolves the session and redirects to `/account` or `/`. `useSession` over the generated
`meOptions()` is the only source of auth truth. The 401 interceptor (`installAuthInterceptor` in
`src/api/client.ts`) settles the `me` query on an anonymous OK envelope and throws `ApiError`; it
never redirects. Only `beforeLoad` guards redirect: the `_authenticated` pathless layout sends
anonymous users to `/` with a `redirect` search param.

Alternatives rejected: CORS with a bearer token in `localStorage` (XSS exposure; the backend has
no CORS config to begin with); a popup flow reading the JSON callback (fragile, still exposes the
token to script); a BFF proxy (an extra service nobody on the team operates).

## Consequences

B1 is open, so until #94 merges the dev workaround is to complete the Google flow, land on the
JSON page and type `/` (the cookie is already set); the `/auth/callback` route is built regardless
because it only refetches `me`. If B1 is refused the escalation is D1 option (c), a backend HTML
bounce page; no frontend token handling will be written either way. A `?redirect=` pass-through
carried in `state` is a follow-up backend ask (B1 optional item): the login link cannot carry a
return URL today, so the callback falls back to `/account`. B3 (#96) leaves the dev cookie
non-Secure, so the secure-cookie NFR is unmet until it merges; HSTS from A2 still applies across
`/api/*`. B5 (#98, `GOOGLE_REDIRECT_URI` on dev) and the cookie flags could not be observed on
2026-09-16 because the dev backend answered 503 (T1-01 probe). B4 (#97) means the header shows
`loginId` only. The starting CSP's `connect-src 'self'` holds only because of this same-origin
rule; the final CSP after T2-11 is recorded here as a dated addendum.

2026-09-17 (branch review, `2d040a2`): on 401 the interceptor uses
`setQueryData(meQueryKey(), { code: OK_CODE, message: 'anonymous' })` instead of `removeQueries`,
so the observed `me` query stays settled rather than pending and refetching on every root
re-render; it must never `invalidateQueries` or `resetQueries` there, because the `me` request's
own 401 would loop. `OK_CODE` is exported from `envelope.ts` for that consumer.
