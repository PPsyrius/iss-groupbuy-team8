# Frontend blueprint review (2026-09-15)

Decision log for the review of `frontend/docs/blueprint.md` before it is turned into an implementation plan. Format: verification findings, then each decision as question → options → decision → rationale, then the blocker list and assumptions. Written so the SWE5006 report can cite it directly.

Participants: Lim Jiechao (frontend owner), Claude Code (reviewer). Method: the blueprint was checked claim-by-claim against the repository, the GitHub issues, and current package registries; every mismatch became either a correction or an interview question. Questions were asked one at a time with a recommended answer.

## 1. Verification findings

The original blueprint was written against `docs/design/repository-structure.md`, which describes an intended repository, not the actual one. Every claim below was checked against code on `dev` at commit `033bd90`.

### 1.1 Repository claims

| Blueprint claim | Reality | Effect |
|---|---|---|
| ADRs 0001–0008 exist in Nygard format | No ADR exists anywhere in the repo | ADR system is greenfield; numbering starts at 0001 |
| Spec lives in `contract/` | Directory is `contracts/`, single file `contracts/openapi.json` | Path fixed everywhere |
| Root `AGENTS.md` and `CLAUDE.md` exist | Neither exists; no `.claude/settings.json`; root `.claude/skills/` has three backend skills | Agent config is greenfield |
| Frontend deployed via Terraform | CloudFront, OAC, and frontend bucket were built by hand with AWS CLI (`docs/deploy/dev-cloudfront-deployment.md`); `infra/terraform` has zero CloudFront resources and excludes dev by design | Ownership question (decision D3) |
| Backend uses `server.servlet.context-path=/api` | No context path. `/api/v1` comes from `ApiConstants.API_V1` on each controller; actuator moved to `/api/actuator` | Vite proxy must not strip the prefix |
| Auth transport is cookie-based | Sa-Token cookie is configured (HttpOnly, SameSite=Lax) but the contract declares `blocbuy-token` as an `apiKey` in `header`, and the OAuth callback returns the token in the JSON body | Decision D1 and backend asks B1–B3 |
| Session probe is a "working name `GET /api/me`" | Already exists as `GET /api/v1/auth/me`, returns `Result<{loginId}>` | OPEN item 2 closed; enrichment ask B4 |
| Renovate with grouped updates | Repo uses Dependabot (daily, Maven + Actions + Docker; no npm) | Dependabot npm ecosystem instead |
| Deploy uses `npm ci` | Runbook §10 says npm; plan uses pnpm | Runbook line to update |

### 1.2 OPEN items in the original blueprint, resolved from code

| OPEN | Answer | Source |
|---|---|---|
| 1. Cookie flags, 401 vs redirect | `http-only: true`, `secure: true`, `same-site: Lax` in `application.yml`; every deployed profile (`local`, `docker`, `ec2`) overrides `secure: false`. Body/header/cookie reading all left at Sa-Token defaults (all enabled). Unauthenticated request → JSON 401 in the `Result` envelope via `GlobalExceptionHandler`, no redirect. `GET /api/v1/products/**` is anonymous. | `backend/blocbuy-bootstrap/src/main/resources/application*.yml`, `SaTokenConfig.java`, `GlobalExceptionHandler.java` |
| 2. `/api/me` accepted and shape | Exists: `GET /api/v1/auth/me` → `{loginId}` | `AuthController.java`, `contracts/openapi.json` |
| 3. Backend CI regenerates contract and fails on drift | Yes. `contract-check` job regenerates with `-Popenapi-export` and runs `git diff --exit-code -- contracts/`, plus `openapi-diff --fail-on-incompatible` bypassable only with the `contract-change-approved` label | `.github/workflows/ci-backend.yml` |
| 4. Running backend locally | Root `docker-compose.yaml` (postgres 18, redis 8, backend on `8589`) or JDK 21 with `application-local.yml` | `docker-compose.yaml` |
| 5. Who owns CloudFront `/api` behavior | Hand-managed by whoever holds AWS credentials (built in PR #23 by l0sgAi); no Terraform owner | Decision D3 |

### 1.3 Facts that make the plan harder than the blueprint expected

- The Google callback is a top-level browser navigation that returns JSON. The cookie is set, but nothing sends the browser back to the SPA. A CSR app cannot intercept that response.
- Every JSON response is wrapped in `Result<T> {code, data, message, errors?, traceId?}`. Generated client types are envelope-shaped.
- `contracts/openapi.json` has `servers[0].url = http://localhost:18080` (the export harness) and `info.version = v0`. The client must override `baseUrl` to `/`.
- The SPA-fallback CloudFront Function is documented as optional in runbook §7.3 and is not known to be attached. No response-headers policy (CSP/HSTS) exists.
- `security.yml` path filters exclude `frontend/**`. There is no frontend workflow at all.
- CODEOWNERS routes `/frontend/`, lockfiles, and `*.ts|tsx|js|jsx|css` to `@limjiechao`, but not `*.json`, `*.mjs`, `*.md`, or `pnpm-workspace.yaml`. `.github/` routes to `@PPsyrius`.
- The GitHub project board (`users/PPsyrius/projects/2`) is private to the current `gh` token; reading it needs `gh auth refresh -s read:project`. The status board inside issue #49 was used as a proxy.

### 1.4 Toolchain claims, verified against registries on 2026-09-15

| Claim | Verdict | Current |
|---|---|---|
| TypeScript 7 on npm as `typescript` with `tsc` | True | 7.0.2; no programmatic API until 7.1; `@typescript/typescript6` 6.0.2 exists |
| HeroUI v3 on React Aria Components, needs Tailwind v4 | True | `@heroui/react` 3.2.5 |
| Tailwind v4 baseline Safari 16.4 / Chrome 111 / Firefox 128 | True | 4.3.3; no official agent skill |
| hey-api valibot + TanStack Query plugins | True | `@hey-api/openapi-ts` 0.99.0; MSW plugin exists (marked in progress); no official skill |
| `@vitejs/plugin-react` with Babel; check for native oxc compiler | Partly outdated | `@vitejs/plugin-react` 6.1.1 on Vite 8.3 supports Babel or native `compiler: true` (experimental); `plugin-react-oxc` is deprecated |
| oxlint JS-plugin mode for TanStack + react-hooks plugins | Partly outdated | oxlint 1.83 ships React Compiler rules natively; JS plugins are alpha; TanStack ESLint plugins untested on it; `--type-aware` via `oxlint-tsgolint` 7.0.2001 is stable |
| oxfmt | Beta | 0.68.0 |
| TanStack Intent skills for react-router, react-query, react-form | False | Only `@tanstack/router-core` (10 skills), `@tanstack/router-plugin` (1), `@tanstack/react-table` (6) ship skills; Intent 0.4.0 |
| pnpm via corepack | Caveat | pnpm 12.4.2; Corepack ships with Node 24 LTS but not Node 25+ |
| Sa-Token defaults | True | 1.46.0; reads body, header, cookie by default |
| Others | Confirmed | React 19.3.0, react-router 1.170.36, react-query 5.102.8, react-form 1.33.5, router-plugin 1.168.38, Vitest 5.0.1, Playwright 1.63.0, knip 6.35.1, Lefthook 2.1.14, MSW 2.15.0, happy-dom 20.14.5 |

## 2. Decisions

### D1. Completing Google login in a CSR app

Question: the callback returns JSON with the token in the body and never redirects to the SPA. How does login complete?

Options: (a) backend 302 to a frontend path after `StpUtil.login`, token removed from body; (b) keep JSON and have the SPA read the token, which requires a popup flow or localStorage and abandons HttpOnly; (c) backend returns an HTML bounce page.

Decision: (a). Backend callback sets the cookie and redirects to `/auth/callback` on the SPA; the SPA calls `GET /api/v1/auth/me` on arrival. Contract security scheme changes to `apiKey in: cookie`.

Rationale: keeps the token unreadable from script, matches the design the backend already committed to in PR #84, and needs a small backend PR rather than a frontend workaround. Gates the authenticated route and the login e2e test.

### D2. Deadline and sequencing

Question: Sprint 1 ended 2026-09-12 with #44 and #42 still To Do; how much toolchain first?

Options: two-tier with the demoable slice first; full blueprint before features; minimal toolchain.

Decision: full blueprint toolchain first, then the demoable slice, both within Sprint 2 (2026-09-14 to 2026-09-25, confirmed by the owner after the interview).

Rationale: the report weights process at 80%; the owner prefers the quality gates in place before feature PRs so every feature PR carries evidence from day one.

### D3. AWS-side ownership

Question: SPA fallback function, CSP/HSTS response-headers policy, and an OIDC deploy role are needed; CloudFront is hand-managed and Terraform excludes dev.

Options: (a) runbook PR, account holder applies; (b) Terraform-import CloudFront now; (c) runbook now, Terraform import as a tier-2 task.

Decision: (c). New numbered steps in `docs/deploy/dev-cloudfront-deployment.md` plus IAM policy examples under `deployment/frontend/iam/`; the account holder executes once. A `frontend-stack` Terraform import is a planned Sprint 2 task linked to #48.

Rationale: keeps the walking skeleton off the infra critical path while still producing full IaC evidence before the report.

### D4. Toolchain corrections

Accepted as stated: `@vitejs/plugin-react` 6 on Vite 8; drop `eslint-plugin-react-hooks` (oxlint native rules); keep the minimal-ESLint fallback as the expected path for the TanStack lint plugins; Dependabot npm ecosystem with `tanstack` and `heroui` groups; Node 24 LTS pinned with `mise`; version pins from §1.4.

Fork: React Compiler via Babel (`babel-plugin-react-compiler` 1.0.0, stable) now; the native `compiler: true` path becomes a tier-2 experiment with its own ADR note.

### D5. Skills manifest

Decision: Intent installed with the `@tanstack/*` scope, which today yields `router-core` and `router-plugin` skills only, and will pick up `react-table` if #31 adopts it. Three authored repo skills: `adr`, `api-client-regen`, `sprint-evidence`. No community packs for Query, Form, or Tailwind. `react-virtual` and `react-pacer` rejected (server-side pagination; trivial debounce). `react-table` deferred to #31.

Verification task in the plan: confirm pnpm's strict `node_modules` lets Intent discover the transitive `router-core`; if not, list it as a direct dependency.

### D6. Agent configuration placement

Decision: `frontend/AGENTS.md` holds conventions and the Intent guidance block; `frontend/CLAUDE.md` contains `@AGENTS.md`. Hooks and permissions go in a new root `.claude/settings.json` with matchers restricted to `frontend/**`, because nested settings only fire when Claude Code is launched from `frontend/`. Authored repo skills live in root `.claude/skills/`. Root `AGENTS.md`/`CLAUDE.md` are a separate team-level PR.

### D7. Frontend CI

Decision: `ci-frontend.yml` mirrors `ci-backend.yml`: dorny paths-filter, `pnpm check` as the required status check, `import.meta.env` grep, `intent stale`, and a `deploy-s3-dev` job on push to `dev` gated by a `frontend-dev` environment and `ENABLE_S3_DEPLOY`, assuming the OIDC role from D3. `security.yml` gains `frontend/**` paths and a dependency scan. Dependabot gains npm. Playwright smoke runs post-deploy against the dev CloudFront URL. All of this is reviewed by the `.github/` owner.

### D8. Walking-skeleton definition

Decision: query = products list and detail (#44); authenticated route = `/account` with a `beforeLoad` guard plus the header login/logout area (#42); mutation = `POST /auth/logout`; form = the listing filter form (category now, keyword when #47 lands) built with TanStack Form + valibot and synced to the URL through `validateSearch`.

Rejected: admin product create form (admin role per database, admin UI out of scope in #32); team-price-quote flow (UC3/UC4 mid-redesign in #50).

### D9. Documentation and evidence

Decision: ADRs live in `frontend/docs/adr/` numbered from 0001. The plan, this review, and a running `frontend/docs/sprint-2-log.md` also live under `frontend/docs/`. Every frontend PR links its issue, names the ADR(s) it implements, includes the `pnpm check` result or CI link, and attaches a screenshot or Playwright trace for UI changes. A PR template under `.github/` and the `sprint-evidence` skill enforce this.

Amended 2026-09-16: the sprint log moved repo-wide to `docs/agile/sprint-02/log.md` and the skill is `agile-evidence-capture`; see plan §12 item 17 and ADR 0008.

## 3. Blocker list

Backend asks (owner l0sgAi unless noted):

| ID | Ask | Gates |
|---|---|---|
| B1 | OAuth callback: after `StpUtil.login`, respond 302 to `/auth/callback` (relative) instead of JSON; remove token from any body | `/account` route, login e2e, header user area |
| B2 | Contract: change `blocbuyToken` security scheme to `apiKey in: cookie`; PR needs the `contract-change-approved` label | Generated client auth typing |
| B3 | `application-ec2.yml`: set `sa-token.cookie.secure: true` now that dev sits behind CloudFront TLS (comment says TLS termination is pending, which is stale) | Secure-cookie NFR, CSP/HSTS policy makes sense only with this |
| B4 | Enrich `GET /auth/me` with `nickname` and `avatarUrl` (already in `LoginResponse`) | Header user area shows more than an id |
| B5 | Confirm `GOOGLE_REDIRECT_URI` for dev is the CloudFront URL `/api/v1/auth/oauth2/google/callback` and that the Google console lists it | Login on dev |

AWS asks (account holder l0sgAi or PPsyrius):

| ID | Ask | Gates |
|---|---|---|
| A1 | Execute runbook steps for the SPA-fallback CloudFront Function on the default behavior | Deep links, e2e |
| A2 | Execute runbook step for a response-headers policy (CSP, HSTS) | Security NFR |
| A3 | Create the `frontend-deploy` OIDC role (S3 put/delete/list on the frontend bucket, `cloudfront:CreateInvalidation`) and store the ARN as a GitHub variable | `deploy-s3-dev` job |
| A4 | Create the `frontend-dev` GitHub environment | Same |

Team asks:

| ID | Ask | Gates |
|---|---|---|
| T1 | A Sprint 2 issue mirroring #49 for 2026-09-14 to 2026-09-25 (dates confirmed) | Sprint log, kanban |
| T2 | `read:project` access to the kanban for the reviewing agent, or a screenshot | Nothing critical; #49 status board is the proxy |
| T3 | PPsyrius review of `.github/` changes (ci-frontend, security paths, dependabot npm, PR template) | CI |
| T4 | CODEOWNERS additions: `*.json`, `*.mjs`, `*.cjs`, `pnpm-workspace.yaml`, `frontend/**/*.md` | Review routing |

## 4. Assumptions carried into the plan

- Sprint 2 runs 2026-09-14 to 2026-09-25 (confirmed). Tier 1 (toolchain and scaffold) targets the first week; tier 2 (features, Terraform import, compiler spike) the second.
- Until B1 lands, local development uses a manual workaround: complete the Google flow once, then navigate to `/`. The cookie is already set by the callback.
- Until B2 lands, the generated client's security typing is ignored; the browser sends the cookie automatically on same-origin requests.
- `Result<T>` handling: keep generated envelope types, unwrap `data` in a shared `select`, throw on non-2xx or non-OK `code`. No generator transform.
- The 401 interceptor clears the `useSession` query and never redirects; only `beforeLoad` guards redirect, so anonymous catalog routes keep working.
- Vite dev proxy target is `http://localhost:8589` with the `/api` prefix preserved.
- The dev CloudFront URL is `https://d1b3np88z657cp.cloudfront.net`.

## 5. Next step

Turn the corrected `frontend/docs/blueprint.md` into `frontend/docs/plan.md` in a planning session using `frontend/docs/plan-session-prompt.md`, then open the ADRs and the backend/AWS asks as issues linked to Sprint 2.
