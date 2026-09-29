# 0002. Same-origin /api with cookie session and OAuth return

Date: 2026-09-16

## Status

Accepted

## Context

The dev distribution serves `/` from S3 and `/api/*` from the ALB (`CachingDisabled`, `AllViewerExceptHostHeader`, all seven methods, `X-Origin-Secret` on the origin leg). The backend has no context path (`/api/v1` comes from `ApiConstants.API_V1`) and zero CORS configuration. Sessions are Sa-Token 1.46 (`token-name: blocbuy-token`; `application.yml` sets `http-only: true`, `secure: true`, `same-site: Lax`, but the `local`, `docker` and `ec2` profiles override `secure: false`; the `ec2` comment "TLS termination pending" is stale since 2026-09-06, B3 #96). Login starts with `GET /api/v1/auth/oauth2/google` (302 to Google plus a `blocbuy-oauth-state` cookie scoped to `/api/v1/auth`); the callback still returns `Result<LoginResponse>` with the token in the body, stranding a top-level navigation on a JSON page. `GET /api/v1/auth/me` returns `{userId, nickname, avatarUrl}` (B4 #97, PR #89), logout is `POST /api/v1/auth/logout`, unauthenticated calls get a JSON 401 envelope, `GET /api/v1/products/**` is anonymous, and the contract still declares `blocbuyToken` as `apiKey in: header` (B2, #95).

## Decision

The SPA calls `/api/*` on its own origin: the generated client's runtime config sets `baseUrl: '/'` and `credentials: 'same-origin'`; locally Vite proxies `/api` to `http://localhost:8589` with the prefix preserved. The session is the HttpOnly `blocbuy-token` cookie set by the backend; the frontend never reads, stores or forwards a token. Login is a full-page navigation to `/api/v1/auth/oauth2/google`; it completes when the backend answers the callback with a 302 to `/auth/callback` (B1, #94; blueprint-review D1 option (a)), where the route invalidates `me`, re-resolves the session and redirects to `/account` or `/`. `useSession` over the generated `meOptions()` is the only source of auth truth. The 401 interceptor (`installAuthInterceptor` in `src/api/client.ts`) settles the `me` query on an anonymous OK envelope and throws `ApiError`; it never redirects. Only `beforeLoad` guards redirect: the `_authenticated` pathless layout sends anonymous users to `/` with a `redirect` search param.

Alternatives rejected: CORS with a bearer token in `localStorage` (XSS exposure, no backend CORS config); a popup flow reading the JSON callback (fragile, still exposes the token); a BFF proxy (an extra service).

## Consequences

B1 is open, so until #94 merges the dev workaround is to complete the Google flow, land on the JSON page and type `/` (the cookie is already set); `/auth/callback` is built regardless (it only refetches `me`). If B1 is refused the escalation is D1 option (c), a backend HTML bounce page; no frontend token handling either way. A `?redirect=` pass-through in `state` is a follow-up backend ask (B1 optional item): the login link cannot carry a return URL, so the callback falls back to `/account`. B3 (#96) leaves the dev cookie non-Secure (secure-cookie NFR unmet until it merges; HSTS from A2 still covers `/api/*`). B5 (#98) and the cookie flags were unobservable on 2026-09-16 (dev 503). B4 (#97) landed in PR #89 (`d875f8c`, header renders `nickname ?? userId`). `connect-src 'self'` holds only because of this same-origin rule.

2026-09-17 (branch review, `2d040a2`): on 401 the interceptor uses `setQueryData(meQueryKey(), { code: OK_CODE, message: 'anonymous' })`, never `removeQueries`/`invalidateQueries`/`resetQueries` (the `me` request's own 401 would loop); `OK_CODE` is exported from `envelope.ts` for it.

2026-09-23 (T2-11, CSP): `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'; upgrade-insecure-requests`. React Aria's injected styles and overlay positioning require `'unsafe-inline'` (preview probe); unused `data:` was removed and `img-src https:` remains for unpinned listing-image and avatar hosts.

2026-09-27 implementation note: B1's 302 and B2's cookie security scheme have landed. `/login` parks a validated same-origin `returnTo` in `sessionStorage` before Google navigation; `/auth/callback` confirms `me` and returns there. `_authenticated` sends anonymous users to `/login?returnTo=...`. The original Decision and earlier consequence paragraphs above document the 2026-09-16 proposal, not today's redirect or token exposure. See `frontend/docs/plan.md` T2-04 and the generated contract.
