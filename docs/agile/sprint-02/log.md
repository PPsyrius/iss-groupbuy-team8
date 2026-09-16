# Sprint 2 log (2026-09-14 to 2026-09-25)

Goal: see the `[T1] Sprint 2` container issue once filed (`frontend/docs/plan.md` T1-02). Tier 1
targets the week ending 2026-09-20, tier 2 the week ending 2026-09-25; code freeze 2026-09-24 EOD.

One entry per unit of work per member, newest at the bottom. Shape and rules:
`.claude/skills/agile-evidence-capture/references/templates.md`. Three to eight lines each.
Say what was verified and what was not.

## 2026-09-16 - @limjiechao - Evidence conventions and agile-evidence-capture skill

- Kind: ops, decision
- Changed: `.claude/skills/agile-evidence-capture/`, `.claude/settings.json` (commit hook),
  `docs/agile/`, `docs/quality/nfr-register.md`, `.github/ISSUE_TEMPLATE/`,
  `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`,
  `frontend/docs/plan.md` (sprint log moved repo-wide, skill renamed)
- Verified: hook script exercised with sample commits (NFR config without evidence -> checklist;
  `style:` and `Evidence-skipped:` -> silent; enforce mode -> exit 2); snapshot script run against
  the live board into a scratch directory. Skill evaluated on four task prompts (login rate
  limiting, pin actions by SHA, UC1 `savingsPercent`, sprint close) in throwaway clones, each run
  with and without the skill and graded against 27 assertions: 27/27 with, 15/27 without; code
  quality equal in both, the difference is entirely the evidence artefacts. Not yet exercised by a
  teammate's session.
- Decisions: ADR `frontend/docs/adr/0008-documentation-and-evidence.md` (Accepted): sprint log and
  NFR register are repo-wide; ADRs stay a frontend practice, the backend keeps its D-table and
  design docs, cross-cutting decisions live in the owning `docs/` page
- Problems: Sprint 1 report shipped with TODO burndown and contribution cells; NFR changes
  (#84, #75, #45) had no issue or doc trail. Action: this change set. Status: in review.
- Next: file the Sprint 2 container issue (T1-02), create `nfr:*` labels, first board snapshot.

## 2026-09-16 - @limjiechao - T1-01 dev CloudFront day-1 probe (#44)

- Kind: ops
- Changed: this log only (zero code). Plan task T1-01 in `frontend/docs/plan.md`.
- Verified: against `https://d1b3np88z657cp.cloudfront.net` at about 13:00 UTC, health probe run
  three times with the same result. Script: three `curl` probes from the T1-01 task row plus the
  root-headers check.

| Probe | Result | Meaning |
|---|---|---|
| GET /api/actuator/health | `503 text/html`, `server: awselb/2.0`, `x-cache: Error from cloudfront` (3 of 3 runs) | `/api/*` behavior reaches the ALB; the ALB has no healthy target. Expected `200 application/json` |
| GET /api/v1/products?page=1 | `503 text/html` | same |
| GET /products/probe | `403 application/xml` (S3 OAC AccessDenied) | A1 SPA fallback attached: no |
| GET / and /index.html | `403 application/xml` | no frontend build in the bucket yet (expected before T1-14) |
| GET /api/v1/auth/oauth2/google | `503`, no `Location`, no `Set-Cookie` | B3 (Secure flag) and B5 (redirect_uri) evidence not obtainable today |
| GET / headers | CSP absent; HSTS absent | A2 absent |

- Problems: dev backend is down. The latest `ci-backend` run on `dev` (merge of #92,
  2026-09-16T12:23Z, run 35095495827) failed at job `deploy-existing-ec2` after `publish-ecr-dev`
  succeeded. Action: raised with @l0sgAi and @PPsyrius; re-run the OAuth probe once dev is healthy
  and append the B3/B5 rows here. Status: open.
- Decisions: none. Note for T2: `gh auth status` shows `read:project` on the current token and
  `gh project view 2 --owner PPsyrius` lists 21 items, so the board is readable at filing time.
- Next: T1-02 file the 13 blocker issues; T1-04 runtime pins.

## 2026-09-16 - @limjiechao - T1-02 Sprint 2 container and blocker issues filed (#93)

- Kind: ops
- Changed: GitHub only. Container #93. Backend B1–B5 = #94–#98 (@l0sgAi). AWS A1–A3 = #99–#101
  (@l0sgAi, @PPsyrius), A4 = #102 (@PPsyrius). Team T2–T4 = #103–#105 (@PPsyrius). Bodies are
  `frontend/docs/plan.md` §10 verbatim plus a "Sprint 2 container issue: #93" line; #103 carries
  a filing-time note that `read:project` is already granted. The T1-01 probe result and the
  failed dev deploy are posted as a comment on #93.
- Verified: the T1-02 task-row count command prints 13.
- Problems: none.
- Next: T1-03 runbook PR (PR-B) so #99–#101 have steps to execute; T1-05 scaffold.

## 2026-09-16 - @limjiechao - T1-04 runtime and package-manager pins (#44)

- Kind: chore, nfr (NFR-17 maintainability: reproducible toolchain; row added after the commit
  hook asked for it)
- Changed: `frontend/.mise.toml`, `frontend/.nvmrc`, `frontend/.npmrc`, `frontend/package.json`
  (`packageManager`, `engines`, empty `scripts`), root `.gitignore` (generated client, Playwright
  outputs, e2e auth state).
- Verified: `corepack pnpm --version` in `frontend/` prints `12.4.2` (binary downloaded on first
  use); `node --version` prints `v24.14.1` (nvm). Not verified: `mise install`, mise is not
  installed on this machine.
- Decisions: ADR 0013 (stub today; body lands in T1-15).
- Next: T1-05 scaffold.

## 2026-09-16 - @limjiechao - T1-03 frontend runbook steps and policy examples (#93, PR-B)

- Kind: ops, nfr (NFR-04 OIDC deploy role reused for the frontend; NFR-16 CSP/HSTS policy source
  now versioned, row stays `documented` until A2 executes in T2-11)
- Changed: `docs/deploy/dev-cloudfront-deployment.md` (§7.3 marked superseded, §10 npm to pnpm
  with the CI-identical release commands, new §14 SPA fallback, §15 response headers, §16 OIDC
  deploy role, §17 `frontend-dev` environment; each section links its blocker A1 #99, A2 #100,
  A3 #101, A4 #102); `deployment/frontend/cloudfront/spa-fallback.js`,
  `deployment/frontend/cloudfront/response-headers-policy.json`,
  `deployment/frontend/iam/README.md` and the two `.json.example` policies. Content is plan §8
  verbatim plus the issue links.
- Verified: T1-03 task-row command on branch `feature/deploy-frontend-runbook`: `python3 -m
  json.tool` ok on all three JSON files, `node --check` ok on `spa-fallback.js`. Not verified: no
  AWS command was run; the steps are for the account holder to execute (dev backend is still down
  per the T1-01 entry, ALB 503), so the §14 and §15 curl probes cannot be exercised yet.
- Decisions: none new; the steps implement ADR 0002 (same-origin, HSTS across `/api/*`), ADR 0004
  (one artifact, `index.html` `no-cache`, immutable assets), ADR 0005 (SPA fallback as a CloudFront
  Function on the default behavior, no distribution-wide error responses) and ADR 0007
  (hand-managed CloudFront changed through the runbook; T2-07 imports these resources into
  Terraform). Bodies land in T1-15.
- Problems: the trust-policy `sub` format is unconfirmed; the backend example carries an
  id-suffixed subject. Action: §16 tells the operator to copy whichever form works for the
  backend role. Status: open until A3 executes.
- Next: user opens PR-B (@l0sgAi runbook, @PPsyrius `deployment/`); A1–A4 executed by the
  account holder; T1-05 scaffold.

## 2026-09-16 - @limjiechao - T1-05 Vite 8 / React 19 / TanStack Router / Tailwind 4 / HeroUI scaffold (#44, #93)

- Kind: feature (scaffold), nfr (NFR-17 row extended with the lockfile; `package.json` and
  `vite.config.ts` are toolchain, not a new NFR)
- Changed: `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/index.html`,
  `frontend/src/{main,router}.tsx`, `frontend/src/styles/app.css`,
  `frontend/src/routes/{__root,index}.tsx`, `frontend/src/api/query-client.ts` (one-line stub,
  replaced in T1-07), `frontend/src/routeTree.gen.ts` (generated, committed read-only, plan §12.16),
  `frontend/package.json` (23 exact pins from plan §1, scripts `dev`/`build`/`preview` only),
  `frontend/pnpm-lock.yaml`. File contents are plan §4 T1-05 steps 2–10 verbatim.
- Verified: on branch `feature/frontend-scaffold-t1-05` off `252ba8b`, node v24.14.1 (nvm),
  pnpm 12.4.2 via `corepack pnpm` (mise not installed). Task-row command
  `pnpm install --frozen-lockfile && pnpm build && test -f dist/index.html && ls dist/assets/*.js`:
  lockfile up to date, `vite v8.3.0` built 240 modules in 548 ms, `dist/index.html` present, four
  JS chunks (`index-*.js`, `routes-*.js`, two vendor chunks) plus a 435 kB CSS bundle (Tailwind +
  HeroUI styles) and two HeroUI font files. `pnpm dev` once: `routeTree.gen.ts` produced with the
  `/` route; headless Chrome (playwright-core 1.63.0 in a scratch dir, not a project dependency)
  against the dev server and against `pnpm preview` of `dist/` rendered `<h1>BlocBuy</h1>` at `/`
  and `<h1>Page not found</h1>` with the "Back to listings" link at `/does-not-exist`; zero page
  errors, one 404 for the absent `favicon.ico`. Not verified: `mise install`; the `/api` proxy
  (dev backend down, T1-01 entry); `tsc --noEmit`, which fails today, see Problems.
- Decisions: ADR 0001 (stack and CSR; `routeTree.gen.ts` committed), ADR 0003 (generated client
  gitignored, no `src/api/generated` yet), ADR 0013 (exact pins, `pnpm-lock.yaml` frozen in CI).
  No new ADR: every file is as planned.
- Problems: (1) `pnpm exec tsc --noEmit` fails with two `TS2688` errors because `tsconfig.json`
  names `vitest/globals` and `@testing-library/jest-dom/vitest`, which arrive in T1-08 and T1-11.
  Action: recorded as plan §12.18, tsconfig kept as written, T1-11 must show `tsc` green before
  PR-C. Status: open until T1-11. (2) `pnpm add` reformatted `engines` in `package.json` onto
  three lines; cosmetic, left as pnpm wrote it. (3) `index.html` has no favicon; cosmetic, out of
  T1-05 scope, noted for the header task (T2-04).
- Next: T1-06 hey-api client generation (separate session, branch to be cherry-picked onto
  `feature/frontend-scaffold`).
