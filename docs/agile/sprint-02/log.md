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
  and append the B3/B5 rows here. Status: closed 2026-09-17, rows in the tier-1 close-out entry below.
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

## 2026-09-16 - @limjiechao - T1-06 generated API client (hey-api openapi-ts) (#44)

- Kind: feature
- Changed: `frontend/openapi-ts.config.ts`, `frontend/src/api/hey-api.ts`, `frontend/package.json`
  (`gen:api`/`prepare` scripts, `build` → `pnpm gen:api && vite build`, `@hey-api/openapi-ts` and
  `msw` exact pins), `frontend/pnpm-lock.yaml`, `frontend/pnpm-workspace.yaml` (new,
  `allowBuilds.msw: true`).
- Verified: `pnpm gen:api` produces 7 files under `src/api/generated` (gitignored, confirmed with
  `git check-ignore`); the task-row command passes; `pnpm build` succeeds. All on commit `760d4d8`.
- Decisions: `@hey-api/openapi-ts` repinned to the exact prerelease `0.0.0-next-20260824173136`,
  not the brief's `0.99.0` — `0.99.0` uses the classic TypeScript compiler API, which
  `typescript@7.0.2` no longer ships (upstream hey-api/hey-api#4235, confirmed open);
  `frontend/pnpm-workspace.yaml` committed so `pnpm install --frozen-lockfile` doesn't fail
  non-interactively on msw's postinstall. ADR 0003 addendum; plan §12 notes 19, 28.
- Problems: attempt 1 with the brief's literal `0.99.0` pin crashed `pnpm gen:api` outright; three
  pnpm-level workarounds (`overrides`, `packageExtensions`, `patch`) failed to isolate a TS6 peer
  for the tool before the prerelease repin resolved it. `msw.gen.ts` exports
  `createMswHandlers().all()`, not the brief's `getAllMocks` — flagged forward to T1-07/T1-08/T1-11.
- Next: T1-08 quality toolchain and the `pnpm check` gate.

## 2026-09-16 - @limjiechao - T1-08 quality toolchain and the `pnpm check` gate (#44)

- Kind: feature, nfr (NFR-18 maintainability: the `pnpm check` gate; row added)
- Changed: `frontend/.oxlintrc.json`, `frontend/.oxfmtrc.json`, `frontend/knip.json`,
  `frontend/vitest.config.ts`, `frontend/src/test/{setup,msw}.ts`,
  `frontend/scripts/ts-nocheck-generated.mjs`, root `lefthook.yml`, `frontend/package.json`
  (scripts and devDependencies), `frontend/tsconfig.json`.
- Verified: `pnpm check` (`gen:api` → `tsc --noEmit` → `oxlint --type-aware` → `oxfmt --check` →
  `vitest run` → `knip`) exits 0 at `aa7b2a6`; `pnpm build` passes; each step also verified
  standalone across three commits.
- Decisions: kept `exactOptionalPropertyTypes: true` project-wide and post-processed
  `src/api/generated/**` with `// @ts-nocheck` instead of relaxing the flag (ADR 0003 addendum);
  `react/react-in-jsx-scope` off (obsolete under the automatic JSX runtime);
  `import/no-unassigned-import` kept at error with an allow-list for `**/*.css` and
  `@testing-library/jest-dom/vitest` (ADR 0006 addendum); oxfmt and lefthook exclude `docs/**` and
  `README.md`; the `e2e` script is deferred to T1-13; knip `ignoreDependencies` temporarily carries
  `@tanstack/react-form` and `@testing-library/user-event`; `allowImportingTsExtensions` was tried
  then removed as unnecessary. Plan §12 notes 20, 22-25, 29.
- Problems: the first `oxfmt` write run reformatted `frontend/docs/*.md` (1130 lines in
  `plan.md`), `docs/globals.css` and `frontend/README.md`; restored and excluded prose from the
  formatter. First commit (`c59dcf7`) left `tsc --noEmit` red on 29 pre-existing
  `exactOptionalPropertyTypes` errors in `src/api/generated/**`; fixed in `f811662`. Task review
  found two lint-rule choices needing justification, not weakening; fixed in `aa7b2a6`.
- Next: T1-07 envelope unwrap, `ApiError`, 401 interceptor, `QueryClient` defaults.

## 2026-09-16 - @limjiechao - T1-07 result envelope unwrap, ApiError, 401 interceptor, QueryClient defaults (#44, #42)

- Kind: feature
- Changed: `frontend/src/api/envelope.ts`, `frontend/src/api/envelope.test.ts`,
  `frontend/src/api/client.ts`, `frontend/src/api/query-client.ts` (replaces the T1-05 stub).
- Verified: `pnpm vitest run src/api` RED (module missing) → GREEN 3/3; `pnpm check` exits 0 at
  `48805af`; `pnpm build` passes.
- Decisions: `OK_CODE = 'COMMON_200_000'` (backend `CommonErrorCode.SUCCESS`), not the brief's
  `'0'` (ADR 0009 addendum); `OK_CODE` kept module-private and `client.ts` drops the brief's
  `export { client }` — both were unused exports per knip, nothing consumes them yet. Plan §12
  note 26; plan's T1-07 "Interfaces produced" line corrected to `installAuthInterceptor(queryClient)`
  to match the brief's own step-4 code, which never took `client` as a parameter.
- Problems: none blocking; three narrow oxlint-driven rewrites (an unsafe-cast avoidance in
  `unwrapResult` and in the interceptor's response-body parsing, a conditional-expect restructure
  in the test) with no behavior change, see the task report for detail.
- Next: T1-11 first component test (Header).

## 2026-09-16 - @limjiechao - T1-11 first component test: Header (#42)

- Kind: feature
- Changed: `frontend/src/components/Header.tsx`, `frontend/src/components/Header.test.tsx`,
  `frontend/src/routes/__root.tsx` (mounts `<Header />` above `<Outlet />`), `frontend/knip.json`
  (`ignoreDependencies` trimmed of `@heroui/react`, `@testing-library/react`).
- Verified: `pnpm vitest run src/components/Header.test.tsx` RED (missing module, then a
  router-context error) → GREEN 1/1; `pnpm check` exits 0 at `828d48b`, vitest 4/4 overall (3
  envelope tests, 1 Header test); `pnpm build` passes. This closes the `tsc` TS2688 problem
  recorded in the T1-05 log entry above.
- Decisions: the login control is HeroUI's `Link` primitive with
  `buttonVariants({ variant: "primary" })`, not `Button as="a"` — `Button` wraps
  react-aria-components' `Button`, which has no `href`/`as` (confirmed against the installed
  `.d.ts`); the test wraps a minimal memory-history `RouterProvider` since `Link` (TanStack
  Router) throws outside one. Plan §12 note 27.
- Problems: none blocking. Not verified: the interceptor's `removeQueries` on the actively
  observed `me` query can cause one extra `/auth/me` request per later re-render after a 401
  (bounded, not a loop); deferred to T2-04.
- Next: PR-C (T1-06..T1-11, plus T1-09/T1-10) not yet opened; branch `feature/frontend-t1-06-11`
  unpushed.

## 2026-09-17 - @limjiechao - Whole-branch review fixes for T1-06..T1-11 (#44, #42)

- Kind: fix, test
- Changed: `e735b6f` (lands the staged T1-06..T1-11 evidence: sprint log, NFR-18, ADR notes, plan
  ticks), `6ad23a8` (`lefthook.yml` exclude globs made repo-root relative), `2d040a2`
  (`frontend/src/api/client.ts` 401 handling, `frontend/src/api/client.test.ts`,
  `frontend/src/components/Header.test.tsx` 200 case, `frontend/vitest.config.ts`,
  `frontend/knip.json`).
- Verified: `pnpm check` (`gen:api` → `tsc --noEmit` → `oxlint --type-aware` → `oxfmt --check` →
  `vitest run` → `knip`) exits 0 at `2d040a2`; `vitest run` 3 files / 7 tests passed; `pnpm build`
  passes. `lefthook run pre-commit` verified twice by hand: docs-only staged ⇒ `frontend-format`
  skips (no files), exit 0; a real `frontend/*.ts` change staged ⇒ the file reaches
  oxfmt/oxlint/tsc as before.
- Decisions: on 401 the interceptor now `setQueryData(meQueryKey(), { code: OK_CODE, message:
  'anonymous' })` instead of `removeQueries` — the observed `me` query stays settled (verified:
  `state.data` survives the fetch's own subsequent error dispatch) instead of hanging pending and
  refetching on every root re-render; never `invalidateQueries`/`resetQueries` there since the
  `me` request's own 401 would loop. `OK_CODE` re-exported from `envelope.ts` for this consumer.
  lefthook's `exclude` globs are matched against repo-root-relative paths even under a command's
  `root:`, so `docs/**`/`README.md` etc. had to become `frontend/docs/**`/`frontend/README.md` to
  actually filter files under `root: "frontend/"`.
- Problems: parked, not fixed here — TanStack Query devtools ship in the production bundle and
  `sourcemap: true` publishes full source maps (both pre-existing from T1-05); needs a plan
  decision on an `import.meta.env.DEV` carve-out (dead-code elimination only) or a lazy import,
  before T1-14.

## 2026-09-17 - @limjiechao - T1-09 TanStack query/router lint rules spike (#44)

- Kind: chore (tooling), nfr (maintainability, NFR-18)
- Changed: `6627430` (`frontend/.oxlintrc.json` gains `jsPlugins` and the four `@tanstack/*` rules,
  `frontend/package.json` + `frontend/pnpm-lock.yaml` add `@tanstack/eslint-plugin-query` 5.102.8
  and `@tanstack/eslint-plugin-router` 1.162.0 as exact devDependency pins,
  `frontend/docs/adr/0006-lint-format-toolchain.md` Consequences, `frontend/docs/plan.md` T1-09
  ticks and §12 note 30); this entry plus the NFR-18 evidence cell in
  `docs/quality/nfr-register.md`. No new NFR row: the gate this touches is already NFR-18, and
  T1-09 only widens its lint step.
- Verified: the plan's own task-row fixture does not work as written (see Decisions), so the
  substituted fixture was used: `cd frontend && printf 'import { useQuery } from
  "@tanstack/react-query"\nexport const useX = (id: number) => useQuery({ queryKey: ["x"],
  queryFn: () => id })\n' > src/lint-fixture.ts; pnpm lint; rc=$?; rm src/lint-fixture.ts; test
  $rc -ne 0` ⇒ `pnpm lint` exits 1 with `src/lint-fixture.ts:2:46: error
  @tanstack/query(exhaustive-deps): The following dependencies are missing in your queryKey: id`,
  so the `test` passes. The router half was checked the same way with a mis-ordered
  `createFileRoute` object (`component`, then `loader`, then `beforeLoad`) ⇒ `error
  @tanstack/router(create-route-property-order): Invalid order of properties for createFileRoute.`
  `pnpm check` (`gen:api` → `tsc --noEmit` → `oxlint --type-aware` →
  `oxfmt --check` → `vitest run` → `knip`) exits 0 at `6627430`; vitest 3 files / 7 tests passed;
  `pnpm lint` on the real sources is clean.
- Decisions: the **oxlint JS-plugins** path won, so step 2 of the plan (a minimal ESLint carrying
  `eslint` 10.10.0 + `eslint-plugin-oxlint` 1.83.0 + `frontend/eslint.config.js`) was not needed
  and no ESLint is configured or invoked; `lint` stays `oxlint --type-aware` and the gate keeps
  one linter. Caveat, found while reviewing the lockfile: both plugins declare `eslint` as a peer
  and pnpm 12 auto-installs peers, so `eslint@10.10.0` does appear in `frontend/pnpm-lock.yaml`
  and `node_modules/.pnpm` as a transitive package — never a direct dependency, never run.
  `6627430`'s commit message says "no ESLint enters the repo", which overstates it; ADR 0006
  Consequences carries the precise version.
  oxlint 1.83.0 loaded both plugins from `.oxlintrc.json`'s `jsPlugins` under Node 24 with no
  flag, no parser and no error text on the first attempt — well inside the 2 h timebox. Rule ids
  are namespaced `@tanstack/query/*` and `@tanstack/router/*` exactly as the plan wrote them
  (oxlint drops the package name's `eslint-plugin-` segment). knip 6.35.1 resolves both plugins
  from `.oxlintrc.json` and does **not** report them unused, so `knip.json` `ignoreDependencies`
  was left alone. Measured cost of hosting the JS rules: `oxlint --type-aware` 0.246 s without the
  plugins vs 0.310 s with them. Recorded as plan §12 note 30 (the task-row fixture) and in ADR
  0006 Consequences (path, evidence, cost, and the alpha/no-semver risk that `jsPlugins` carries).
- Problems: the task-row fixture in plan §3/§4 can never satisfy its own acceptance criterion —
  `export const x = () => useQuery({ queryFn: () => 1 })` does fail `pnpm lint`, but on
  `react-hooks(rules-of-hooks)` (`x` is neither a component nor a hook name), not on a
  `@tanstack/query/*` rule; rename it to `useX` and lint goes green, because
  `@tanstack/query/exhaustive-deps` only fires when the `queryFn` closes over something the
  `queryKey` omits, and `() => 1` closes over nothing. Substituted as above and written up as §12
  note 30, so a future reader does not conclude the plugins failed to load.
- Next: T1-10 agent configuration; PR-C not yet opened.

## 2026-09-17 - @limjiechao - T1-10 Agent configuration (#44)

- Kind: ops/process, decision (ADR 0012)
- Changed: `frontend/AGENTS.md`, `frontend/CLAUDE.md` (`@AGENTS.md`), root `.claude/settings.json`
  (merged, not overwritten), `.claude/hooks/frontend-{fix,typecheck,bootstrap}.sh`,
  `frontend/package.json` (`intent` key), `frontend/docs/tanstack-skills.json`,
  `frontend/.oxfmtrc.json`, `lefthook.yml` (formatter ignores for the Intent-managed files),
  ADR 0012 + `frontend/docs/adr/README.md`, plan §11/§12 and the T1-10 boxes, this log, NFR-18 evidence.
- Verified: **Intent discovery.** With no `intent` key, `pnpm dlx @tanstack/intent@0.4.0 list`
  found `@tanstack/router-core` 1.171.30 with **10** skills (transitive, under
  `node_modules/.pnpm/@tanstack+router-core@1.171.30/...`), `@tanstack/router-plugin` 1.168.38
  with 1, `@tanstack/virtual-file-routes` 1 , `@tanstack/devtools` 4, `@tanstack/devtools-event-client`
  3, `dotenv` 2 — so the step-3 fallback (`pnpm add @tanstack/router-core`) was **not** needed.
  After `intent.skills: ["@tanstack/*"]` + the three excludes: router-core 9, router-plugin 1,
  virtual-file-routes 1 (11 skills total in the committed manifest).
  **CLI invocations** (non-interactive, no TTY):
  `pnpm dlx @tanstack/intent@0.4.0 install` → "Updated AGENTS.md with skill loading guidance.";
  `pnpm dlx @tanstack/intent@0.4.0 install --map` → "Updated AGENTS.md with 11 mappings.";
  **Acceptance test.** The task row's verbatim jq filter fails —
  `jq: error (at <stdin>:144): Cannot index array with string "packageName"` — because
  `list --json` returns an object, not an array; and the `>= 10` threshold is `>= 9` once
  `router-core/ssr` is excluded. The working form
  (`... | jq -e '[.skills[] | select(.packageName=="@tanstack/router-core")] | length >= 9'
  && grep -q intent-skills AGENTS.md
  && bash -n ../.claude/hooks/*.sh`) prints `true` and exits 0. Written up as plan §12 note 31(e).
  **Hook simulations** (stdin payloads piped to each script with `CLAUDE_PROJECT_DIR` set):
  `frontend-fix.sh` turned `const   t1_10Probe   =    {a:1,   b:2,c:3}` appended to
  `src/routes/index.tsx` into `const t1_10Probe = { a: 1, b: 2, c: 3 };` and exited 0 (file then
  restored; `git status` clean for `frontend/src`). `frontend-typecheck.sh`: exit 0 on
  `{"stop_hook_active":true}`; exit **2** on a probe `export const bad: number = "not a number"`
  with `TS2322: Type 'string' is not assignable to type 'number'.` on stderr; exit 0 once the
  probe was deleted. `frontend-bootstrap.sh`: after `rm -r frontend/src/api/generated` it printed
  "frontend: generated client was missing; ran pnpm install (prepare → gen:api)." and the 9
  generated entries were back; the second run printed nothing (idempotent). `bash -n` clean on all
  three; all three are 0755.
  **Review fix round 1** (second commit on the branch). The review found `jq` is an undeclared
  hard dependency of two hooks: it is pinned nowhere in this repo and is absent from a stock
  macOS, and under `set -euo pipefail` the `jq` substitution aborts the script with **127** before
  its intended `exit 0`. Reproduced with a scratch PATH holding only symlinks to bash/sh/cat/
  printf/git/pnpm — `command -v jq` fails there, and the unguarded equivalent
  (`set -euo pipefail; input="$(cat)"; file="$(printf … | jq -r …)"`) exits 127. With
  `command -v jq >/dev/null 2>&1 || exit 0` added to `frontend-fix.sh` and
  `frontend-typecheck.sh`, both now exit **0** on that PATH with 0 bytes on stdout and 0 bytes on
  stderr. The review also found the `permissions.deny` list evaded by the exact commands it
  exists to stop: rules are prefixes, so bare `pnpm publish`, `git push --force` and
  `git push -f` matched no deny rule (and `pnpm publish` was caught by `Bash(pnpm *)` in `allow`),
  and `--force-with-lease` was uncovered; exact-match siblings were added and the `.env` reads
  widened to `Read(./**/.env{,.*})`. `jq . .claude/settings.json` parses; the 11 deny rules read
  back as written. `frontend-fix.sh` also gained a relative-path normalisation
  (`case "$file" in /*) ;; *) file="$root/$file" ;; esac`), verified by driving it with
  `"file_path":"frontend/src/routes/index.tsx"` — it now reformats the file instead of silently
  missing `frontend/frontend/...`. All three hook simulations re-run green afterwards.
  **Gate.** `pnpm check` (gen:api → tsc --noEmit → oxlint --type-aware → oxfmt --check →
  vitest run → knip) exits **0**; vitest 3 files / 7 tests passed; knip reports only the six
  pre-existing configuration hints. `git check-ignore -v` on the five files that must be
  committed exits 1 (nothing ignored).
- Decisions: ADR 0012 (Accepted, 2026-09-17). Conventions in `frontend/AGENTS.md` with a one-line
  `CLAUDE.md` import; hooks scoped to `frontend/**` by the per-handler `if` field *and* a check
  inside each script, so nothing fires on backend or infra edits; root `.claude/settings.json`
  merged so the `agile-evidence-capture` `PreToolUse` hook survives verbatim; TanStack skills
  discovered at runtime and allowlisted in
  `package.json#intent`; `router-core/ssr` excluded (pure CSR, ADR 0001) along with the devtools
  plugin-authoring skills; the committed manifest stores repo-relative paths so the planned
  T1-12 `intent-stale` diff is machine-independent. Review round 1 diverges from §6.3/§6.4 on two
  points (plan §12 note 31(g),(h)): hooks degrade to a no-op when `jq` is missing rather than
  assume it, and `permissions.deny` carries exact-match forms alongside the `*` forms. Plain
  `git push` stays out of `deny` deliberately — teammates push feature branches, and the
  user-local git-guard hook is a separate layer; refspec (`git push origin +main`) and trailing
  `--force` spellings remain uncovered by prefix rules and fall through to a prompt.
- Problems: (1) the in-session hook smoke — plan T1-10 step 5 as written, from a Claude Code
  session started at the repo root — could not be done from this headless run and is **owed to
  the user's next Claude Code session at the repo root**; step 5 stays unticked. (2) §6.4's
  bootstrap script was a false positive: `pnpm install --frozen-lockfile` skips `prepare` when
  `node_modules` is warm, so it reported success while the client stayed missing; fixed to run
  `gen:api` explicitly and report on the directory. It also tested `-d "$root/.git"`, which is
  false inside a worktree, and grepped for a `lefthook.checksum` that lefthook 2.1.14 does not
  write. (3) `oxfmt --check` rewrites the Intent-managed block in `AGENTS.md`, so `AGENTS.md` and
  `CLAUDE.md` were added to the oxfmt ignore list and to `lefthook.yml`.
  All five are plan §12 note 31.
- Next: T1-13 Playwright smoke. PR-C and PR-D not opened.

## 2026-09-17 - @limjiechao - T1-13 Playwright smoke (#44)

- Kind: test (NFR: testability, ADR 0011)
- Changed: `frontend/playwright.config.ts`, `frontend/e2e/smoke.spec.ts`, `frontend/package.json`
  (`e2e` script, `@playwright/test` devDependency), `frontend/pnpm-lock.yaml`, `frontend/AGENTS.md`
  (Commands bullet), `frontend/docs/adr/0011-testing-strategy.md` (Consequences), `frontend/docs/plan.md`
  (T1-13 boxes, §12 note 32), `docs/quality/nfr-register.md` (NFR-18 evidence), this log.
- Verified: **Install.** `corepack pnpm install --frozen-lockfile` from `frontend/` (pnpm 12.4.2,
  matches `packageManager`) exited 0, ran `prepare` → `gen:api` (7 files generated,
  19/19 `// @ts-nocheck` stamped), "Done in 2.9s using pnpm v12.4.2". **Pin check.**
  `pnpm view @playwright/test@1.63.0 version` → `1.63.0`; exact pin used as written, no repin
  needed. `pnpm add -D @playwright/test@1.63.0` added devDependencies `@playwright/test 1.63.0`;
  lockfile diff adds only `@playwright/test`, `playwright` and `playwright-core` at `1.63.0`.
  **Browser install.** `pnpm exec playwright install --with-deps chromium` succeeded directly on
  macOS (Chrome for Testing 153.0.8010.12, Chrome Headless Shell, FFmpeg all downloaded; no sudo
  prompt, `--with-deps` a no-op as expected, so the plain-`install chromium` fallback was not
  needed). **Build.** `pnpm build` (`gen:api && vite build`) exited 0, "built in 616ms",
  `dist/index.html` + hashed assets written. **Local e2e.** `pnpm build && pnpm e2e` (no
  `E2E_TARGET`, `webServer` runs `vite preview --port 4173 --strictPort` against `dist/`): list
  reporter shows `1 passed` (home renders the BlocBuy header) and `2 skipped` (the whole `dev
  CloudFront only` describe via `test.skip(!isDev, …)`), exit 0; no trace produced (`trace:
  'on-first-retry'` with `retries: isDev ? 1 : 0` never retries locally, as expected — not forced).
  **Dev-target attempt (owed, run anyway for real evidence).**
  `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e`: `2 failed`, `1
  skipped` (the fixme'd deep-link test reports as skipped), exit 1. Home test failed —
  `getByRole('link', { name: 'BlocBuy' })` never appeared because `https://d1b3np88z657cp.cloudfront.net/`
  returns 403 (no `dist/` deployed until T1-14). Health test failed — `expect(res.status()).toBe(200)`
  got `503` (`/api/actuator/health` through CloudFront; matches the controller's 2026-09-17 probe).
  Because `E2E_TARGET=dev` sets `retries: 1`, this run's failures produced real `trace.zip` and
  screenshot attachments under `test-results/`, confirming the `trace: 'on-first-retry'` wiring.
  **Gate.** `pnpm check` (`gen:api` → `tsc --noEmit` → `oxlint --type-aware` → `oxfmt --check` →
  `vitest run` → `knip`) exits **0**: `tsc --noEmit` clean (after the `webServer` fix below);
  `oxlint --type-aware` clean, no override needed for `e2e/**` (the `vitest` plugin did not
  misfire on `@playwright/test`'s `test`/`expect`); `oxfmt --check` "All matched files use the
  correct format" on 30 files; `vitest run` 3 files / 7 tests passed; `knip` reports only the same
  four pre-existing configuration hints (no unlisted `playwright` binary or `@playwright/test`
  dependency — the existing `e2e/**/*.spec.ts` and `playwright.config.ts` entries already cover
  them). `git check-ignore -v frontend/playwright-report frontend/test-results` confirms both are
  ignored (`.gitignore:89-90`); `git status --short` shows nothing under either directory.
- Decisions: no new ADR; ADR 0011 gained a dated Consequences line. One deviation from the brief's
  verbatim `playwright.config.ts`, required to keep `pnpm check` green: the brief's
  `webServer: process.env.E2E_BASE_URL ? undefined : {...}` fails `tsc --noEmit` under this repo's
  `exactOptionalPropertyTypes: true` (`TS2769`: explicit `undefined` is not assignable to an
  optional property under that flag, distinct from an absent key). Rewritten as
  `...(process.env.E2E_BASE_URL ? {} : { webServer: {...} })` so the key is omitted rather than
  set to `undefined`; identical runtime behaviour. Plan §12 note 32 carries the full note. `e2e`
  was added to `frontend/package.json#scripts` but deliberately **not** to `pnpm check` (plan §12
  note 24: it is a separate, slower gate that needs a built `dist/`).
- Problems: dev CloudFront is not usable today — root returns 403 (no `dist/` until T1-14) and
  `/api/actuator/health` returns 503 (dev backend down) — so the task-row's second verification
  command (`E2E_TARGET=dev … pnpm e2e`) is **owed**: re-run once T1-14 deploys and the backend is
  up. A1 (#99, SPA-fallback CloudFront Function) is open, so the deep-link test carries
  `test.fixme(true, 'A1 not attached: https://github.com/PPsyrius/iss-groupbuy-team8/issues/99')`
  and stays in the file rather than being deleted, per plan T1-13's instruction to revert once
  T1-01's re-probe shows 200.
- Next: T1-12 `.github` PR-E (needs @PPsyrius), T1-14 first deploy. PR-C, PR-D and PR-F not opened.

## 2026-09-17 - @limjiechao - T1-12 .github frontend CI pipeline (#44, #104, #105, PR-E)

- Kind: nfr, ops
- Changed: `.github/workflows/ci-frontend.yml` (new: `detect-changes`, `frontend-check`,
  `intent-stale`, `e2e-preview`, `deploy-s3-dev`, `e2e-dev`), `.github/workflows/security.yml`
  (frontend path filters, pnpm-lockfile Trivy scan + SARIF), `.github/dependabot.yml` (npm
  `/frontend`), `.github/CODEOWNERS` (§7.4 block, `/.github/` anchor), `CONTRIBUTING.md` (required
  checks, Frontend subsection), `frontend/docs/plan.md` (T1-12 boxes 1–4, §12 note 33),
  `docs/quality/nfr-register.md` (NFR-17/18 evidence, new NFR-19), this log. `.gitignore` (§7.5)
  and the PR template (§7.6) already satisfied the plan; untouched.
- Verified: actionlint 1.7.12 twice from the worktree root — the release binary, then
  `docker run … rhysd/actionlint@sha256:b1934ee5…` (the digest `security.yml`'s `workflow-lint`
  pins; ShellCheck 0.11.0 bundled): both exit 0, no findings, all five workflows. YAML parse of the
  five workflows + `dependabot.yml` via `pnpm dlx yaml@2.9.1 valid` (PyYAML absent): all ok.
  `intent-stale` step dry-run in this worktree after `corepack pnpm install --frozen-lockfile`
  (pnpm 12.4.2): manifest diff clean, `grep -q intent-skills AGENTS.md` exit 0. The "Forbid
  import.meta.env" grep: no hits in `src`/`e2e`. `package_json_file` (pnpm/action-setup) and
  `node-version-file`/`cache`/`cache-dependency-path` (setup-node) confirmed against `action.yml`
  at the pinned commits. `pnpm check` not re-run (no `frontend/` source touched). **Not verified:**
  no GitHub run yet — a green `frontend-check` on PR-E is owed.
- Decisions: cross-cutting, no ADR; shape per plan §7 and ADRs 0004 (build once, promote the same
  `dist/` to S3), 0008 (evidence), 0013 (toolchain pins, now enforced in CI), bodies in T1-15. One
  required check (`frontend-check`) with `no-runtime-env` folded in as a step; `deploy-s3-dev`
  gated on `ENABLE_S3_DEPLOY` until A3/A4 (#101, #102) land. Deviations: plan §12 note 33.
- Problems: `corepack pnpm install --dir frontend` ran pnpm 10.33.4 and `engines` rejected it
  (NFR-17 doing its job; run from `frontend/`). Dependabot's pnpm-12 lockfile support is
  undocumented (plan §12.4): "Check for updates" after the first merge. PR-E, review and the
  branch-protection ask (step 5) are the user's and @PPsyrius's; the five `frontend-dev` variables
  (runbook §17) must exist before `ENABLE_S3_DEPLOY=true`.
- Next: user opens PR-E; T1-14 first deploy once A3/A4 close; T1-15 ADR bodies (next entry, same
  branch).

## 2026-09-17 - @limjiechao - T1-15 ADR 0001–0013 bodies and blocker table (#93)

- Kind: decision, ops
- Changed: `frontend/docs/adr/0001..0013-*.md` (Context, Decision, Consequences written; Status
  `Accepted`; 0008 and 0012 unchanged), `frontend/docs/adr/README.md` (Status column),
  `frontend/docs/plan.md` (T1-15 steps 1–4 ticked, §12 note 34), this log.
- Verified: `grep -L '^## Decision' frontend/docs/adr/0*.md | wc -l` prints 0; `wc -l
  frontend/docs/adr/0*.md` shows every file under 60 lines; an `awk` pass counts 13 files whose
  first non-empty line after `## Status` is exactly `Accepted` (the command is in plan §12 note 34).
  The apply script that produced this commit runs all three and refuses to commit unless they hold,
  and prints the `wc -l` table. The plan's literal second command, `grep -l 'Status: Accepted'
  frontend/docs/adr/0*.md | wc -l`, was adjusted: the Nygard layout puts `## Status` and `Accepted`
  on separate lines, so that grep prints 0 even for the two ADRs that were already Accepted. Not
  verified: nothing was run against AWS, the backend or `pnpm`; every fact in the bodies is cited
  to plan §9/§12, the entries above, the config files, `contracts/openapi.json`,
  `AuthController.java`, `CommonErrorCode.java` and `application*.yml`, all read at `beffa0e`.
- Decisions: all thirteen ADRs are Accepted (0008 since 2026-09-16, 0012 since T1-10). Bodies that
  record a fact changed since the plan's §9 sentence: 0001 (`routeTree.gen.ts` committed; T2-09
  addendum slot open), 0002 (the 401 interceptor uses `setQueryData`, not `removeQueries`; B1 and
  B3 still open), 0003 (hey-api pinned to the `next` snapshot, `// @ts-nocheck` post-processing,
  `createMswHandlers().all()`), 0004 (devtools-in-bundle and source-map question parked before
  T1-14), 0006 (oxlint `jsPlugins` won, no ESLint, transitive `eslint` in the lockfile), 0009
  (`OK_CODE` is `COMMON_200_000` and exported since `2d040a2`), 0010 (`@rolldown/plugin-babel` +
  `reactCompilerPreset()` carrier; T2-08 addendum slot open), 0011 (`pnpm e2e` outside `pnpm
  check`; dev run owed), 0013 (Dependabot npm entry lands in T1-12; pnpm 12 lockfile parsing
  unverified). 0005 and 0007 match §9 as written.
- Problems: the implementing agent was pinned to the T1-12 worktree by the worktree-isolation hook
  and could neither run a shell nor write into `feature/frontend-t1-15`; it authored the files in
  the job scratch directory and the orchestrating session applied them (copy, plan edit, the three
  checks above, `git add -A`, `git commit -F`) as a second commit on `feature/frontend-t1-12`,
  stacked on T1-12 (`a300368`), so `feature/frontend-t1-15` and its worktree are redundant. The
  orchestrating session re-checked the bodies' concrete claims against the tree before applying
  (env grep empty, interceptor and envelope details, Vite/lint/test config, every version pin, 7
  tests in 3 files) and re-ran `gh issue list --state all`: all twelve blockers open, as tabled.
  Dev backend still down (T1-01 entry); changes nothing here.
- Next: user opens PR-E from this branch; T1-14 first deploy after A3/A4; T2-08 addendum for 0010;
  T2-09 addendum for 0001; T2-11 CSP addendum for 0002.

Blocker state at T1-15 (container #93; `gh issue list --limit 100 --state all`, 2026-09-17):

| Id | Issue | Title (short) | Owner | State (2026-09-17) |
|---|---|---|---|---|
| B1 | #94 | OAuth callback 302 to `/auth/callback` | @l0sgAi | open |
| B2 | #95 | Contract `blocbuyToken` as `apiKey in: cookie` | @l0sgAi | open |
| B3 | #96 | `application-ec2.yml` cookie `secure: true` | @l0sgAi | open |
| B4 | #97 | Enrich `/auth/me` with `nickname`, `avatarUrl` | @l0sgAi | open |
| B5 | #98 | Confirm dev `GOOGLE_REDIRECT_URI` | @l0sgAi | open |
| A1 | #99 | Attach SPA-fallback CloudFront Function | @l0sgAi, @PPsyrius | open |
| A2 | #100 | Response-headers policy (HSTS, CSP) on dev | @l0sgAi, @PPsyrius | open |
| A3 | #101 | `BlocBuyGitHubFrontendDeployDev` OIDC role | @l0sgAi, @PPsyrius | open |
| A4 | #102 | `frontend-dev` environment and variables | @PPsyrius | open |
| T2 | #103 | `read:project` access or weekly board screenshot | @PPsyrius | open |
| T3 | #104 | Review `.github` changes for the frontend pipeline | @PPsyrius | open |
| T4 | #105 | CODEOWNERS for frontend toolchain files | @PPsyrius | open |

## 2026-09-17 - @limjiechao - PR #106 green-up: Header B4 fix and knip lefthook reference (#42, #97, #44)

- Kind: fix, ops/process (CI gate)
- Changed: `frontend/src/components/Header.tsx`, `frontend/src/components/Header.test.tsx`
  (cherry-picked B4 fix: render `nickname` with a `userId` fallback, test id `session-user`);
  `frontend/package.json` (`hooks:install` script), `frontend/AGENTS.md` (git-hooks line), this log.
- Verified: `frontend-check` on `d875f8c` (run 35229148035): `tsc`, `oxlint`, `oxfmt` and vitest
  (3 files, 8 tests) pass; `knip` fails with "Unused devDependencies (1): lefthook". Locally
  `pnpm check` was green on the same commit. Cause read from knip's lefthook plugin: it only looks
  for `lefthook.yml` in the knip cwd (`frontend/`) or for installed `.git/hooks`; our config is at
  the repo root and the CI checkout has no hooks, so nothing references `lefthook`. After adding
  `"hooks:install": "lefthook install"` (a script reference knip's binary detection counts),
  `pnpm check` exits 0 locally; the CI run on this commit is the real proof.
- Decisions: a real script reference over `knip.json#ignoreDependencies`, so the dependency stays
  checked and the install command is documented in one place; AGENTS.md now says
  `pnpm hooks:install`. No ADR change (ADR 0006 already owns knip); NFR-18 unchanged.
- Problems: the knip failure was masked on every earlier CI run because `tsc` failed first on
  `loginId`, so the gate had never reached knip on GitHub before this commit.
- Next: user pushes; watch `frontend-check`; the `frontend-check` always-report change and the
  Trivy pnpm scan restore from the local rebase branch are still to be replayed.

## 2026-09-17 - @limjiechao - PR #106 review: stale `MeResponse` docs after B4 (#97, #44)

- Kind: docs (review fix, CodeRabbit r4030090764)
- Changed: ADR 0002 Context (`/auth/me` shape) and Consequences (header display), `blueprint.md`
  networking bullet (dated correction) and blocker 4, plan §1 auth line and the B4 blocker row.
- Verified: `contracts/openapi.json` on dev and on the branch defines `MeResponse` as
  `{userId, nickname, avatarUrl}` (PR #89, 2026-09-16); `Header.tsx` renders `nickname ?? userId`
  since `d875f8c`. ADR 0002 stays at 59 lines (T1-15 keeps every ADR under 60), so the correction
  is in place, not an added addendum. No code change; `pnpm check` not affected.
- Decisions: blueprint keeps its original wording with a dated correction (it is a snapshot);
  the plan and the ADR say the current contract outright. `blueprint-review.md` rows and the
  B4 issue draft in plan §12 are left as written: they describe the state on the day.
- Next: T2-04 renders `avatarUrl`; #97 closure is on the backend owner.

## 2026-09-17 - @limjiechao - PR #106 review: evidence hook honours `git -C <dir> commit` (#44)

- Kind: fix (agent tooling), review fix (CodeRabbit r4030090657)
- Changed: `.claude/skills/agile-evidence-capture/scripts/commit-evidence-hook.sh`: the matcher
  already accepted `git -C <dir> commit`, but `rev-parse`, `diff --cached` and `status` ran
  against the hook `cwd`. The `-C` path is now extracted once (quotes stripped, `~` and relative
  paths resolved against cwd) into `repo_dir`, and the three git calls use it. Commands without
  `-C` keep `repo_dir = cwd`.
- Verified: six payloads piped to the old and new script (temp repo with a staged `src/x.ts`):
  absolute `-C` from a non-repo cwd, relative `-C` from the parent, quoted `-C` behind an env
  prefix and a `&&` chain now report "Sprint log" missing (old reported only "Issue reference");
  plain `git commit` from the repo and from a non-repo cwd unchanged; enforce mode exits 2 with
  the checklist on stderr; `git -C /x status` passes through with exit 0. `bash -n` clean;
  shellcheck not installed here.
- Decisions: resolve `-C` rather than stop matching it, so worktree-style commits from a
  different cwd still get the evidence nudge. Not an NFR (agent-side advisory hook).
- Next: none; the hook's enforce mode remains opt-in.

## 2026-09-17 - @limjiechao - PR #106 review: exact package-manager allowlist in `.claude/settings.json` (#44)

- Kind: security (agent permissions), review fix (CodeRabbit r4030090644, CWE-78)
- Changed: root `.claude/settings.json` `permissions.allow`: `Bash(pnpm *)`, `Bash(corepack *)`
  and `Bash(mise *)` replaced by 27 exact commands (repo scripts, the four `pnpm exec` gate
  tools, pinned `pnpm dlx @tanstack/intent@0.4.0` list/install forms, `corepack enable` /
  `pnpm --version` / `install --frozen-lockfile` / `check`, `mise install`); plan §12 note 31(i).
- Verified: `jq -e '.permissions.allow|length'` prints 42 (27 exact + 15 unchanged git/gh
  rules); the `deny` block, hooks and every other key are byte-identical (`git diff` touches
  only the `allow` array). The 27 commands cover every pnpm/corepack/mise invocation named in
  `frontend/AGENTS.md`, plan §6, the three hook scripts and `ci-frontend.yml` except the ones
  deliberately left to a prompt.
- Decisions: exact strings, no wildcards, as the review asked; `pnpm add`, `pnpm exec
  playwright install`, `pnpm dev`/`preview` and the Intent block's `pnpm dlx
  @tanstack/intent@latest load …` prompt on first use. `pnpm publish` stays in `deny`. Not a
  runtime NFR (agent-session permission surface); ADR 0012 unchanged, plan note 31(i) records it.
- Next: none.

## 2026-09-17 - @limjiechao - PR #106 review round r5236778340: thirteen CodeRabbit findings, one commit each (#44)

- Kind: review fix (CI, runbook, ADR, design brief, plan, agent skill, Playwright)
- Changed, in commit order:
  `security.yml` gets back the `frontend/package.json` + `frontend/pnpm-lock.yaml` deps filter
  entries and the Trivy pnpm scan the rebase dropped, NFR-19 evidence cell updated (r4037739726);
  runbook §14 and its older copy plus plan §7 `create-function`: `Runtime` inside
  `--function-config`, `Autopublish` removed, `publish-function` with the returned ETag
  (r4037739679); runbook §16 decodes the `frontend-dev` OIDC `sub` from a real token instead of
  copying the backend role's, ADR 0007 consequence aligned (r4037739708); ADR 0007 states that
  `sensitive` only redacts output and names the state controls (r4037739744); skill templates use
  `YYYY-MM-DD` (r4037739598); SKILL.md commands use the repo-relative `snapshot-board.sh` path
  (r4037739647); design brief source list points at `frontend/docs/blueprint.md` (r4037739802);
  blueprint-review `{loginId}` rows marked historical and the brief's backend ask trimmed to
  roles (r4037739761); accent-fill rule decided, see below (r4037739774); per-team joins on
  listing detail moved to Tier 2 (r4037739788); plan T1-06 synced with the shipped generator pin,
  `gen:api` stamp and `createMswHandlers().all()` (r4037739812); plan session examples and B4
  draft off `loginId` (r4037739819); Playwright web server builds `dist/` when missing, AGENTS.md
  and CONTRIBUTING updated (r4037739834).
- Verified: `security.yml` diff is byte-identical to the T1-12 commit `56f7ec7` (blob `d9b0381`,
  actionlint-clean then; actionlint not installed here). Playwright: `dist/` moved aside, `pnpm
  e2e` ran `gen:api` + `vite build` then preview, 1 passed / 2 skipped; second run with `dist/`
  present did not build; lefthook oxfmt, oxlint `--type-aware` and `tsc --noEmit` green on the
  commit. OIDC decode one-liner tested on a sample JWT payload. Docs-only commits: `grep` for
  `loginId`, `getAllMocks`, `Autopublish`, `BLUEPRINT.md` and `scripts/snapshot-board.sh` after
  each edit. Not run on GitHub: `trivy-deps` will only fire on this PR once a `frontend/` lockfile
  or manifest change is pushed.
- Decisions: accent fill is owned by the team price, the team progress and the single primary
  action per screen (`Button variant="primary"`), so `button-primary` keeps the accent and the
  brief says so (confirmed by the owner on 2026-09-17; the header sign-in control is neutral, so
  the shipped `Header.tsx` `variant: "primary"` on Log in is a T2-04 restyle item); Tier 1 detail shows one Join for the best open team, the per-team list is Tier 2
  and the open-team list endpoint joins the §12 backend asks; snapshot documents
  (`blueprint-review.md`, the B4 draft) keep their day-of wording with dated notes rather than
  being rewritten; the B4 "session fields" sub-item was not changed because B4 has landed (PR
  #89) and the brief's data section is already true. Trailers: each fix commit carries
  `Evidence-skipped` pointing at this entry.
- Problems: `frontend/dist.bak` (the moved-aside build used for the Playwright check) is left in
  the working tree because deleting it was refused in this session; remove it by hand. The git
  guard rejects commit messages containing the word "restore", hence "re-add" in the CI commit.
- Next: push and reply on the review threads; ask @PPsyrius whether the frontend Trivy scan stays
  inside `trivy-deps` (as re-added) or becomes its own job (#108 follow-up).

## 2026-09-17 - @limjiechao - Tier 1 close-out after PR #106: dev evidence, `frontend-check` required-check shape, Dependabot guard (#93, #104, #44)

- Kind: ops and NFR-01/16/19; CI shape recorded in `ci-frontend.yml` and plan note 35.
- Changed: restored the #108 every-PR `frontend-check`, ignored incompatible `openapi-ts`
  releases, updated the three NFRs and plan status, and captured `2026-09-17.csv` (35 issues).
- Verified on `ce65abe`: health/products 200; `/` and `/products/probe` 403; OAuth start 302 with
  the correct callback and `blocbuy-oauth-state` carrying `HttpOnly; SameSite=Lax`, not `Secure`.
  Dev E2E: 1 passed health, 1 failed home 403, 1 fixme deep link. Push run 35239337645 was green
  with delivery disabled; `trivy-deps` run 35238582370 was green on `f7b3d65`; actionlint,
  Dependabot YAML and `tsc --noEmit` passed. Root-session edit/bootstrap hooks also fired.
- Dependabot opened #111/#112, proving pnpm 12 parsing, but both hit hey-api/hey-api#4235 under
  TypeScript 7. Alert GHSA-hhx9-57xq-r5rw is an inaccurate version-ordering match: the pinned
  snapshot postdates 0.97.3 and contains its `Object.create(null)` fix.
- Not verified: the first backend-only "not applicable" run, automatic closure of #111/#112, or
  frontend deployment. Add `frontend-check` to `dev-require-review` only after this PR merges.
- Problems: T1-14 remains blocked by A3 #101/A4 #102; B3 #96 remains unmet; #94/#95/#97 landed in
  PR #89 but still need closing.

## 2026-09-18 - @limjiechao - PR #113 review follow-up: scheduled board snapshot (#113, #103, #93)

- Kind: ops/process and NFR-01 documentation; design and rejected alternatives are in the CI
  plan; no ADR.
- Changed: added the daily/manual workflow, rolling `metrics/board-snapshots` PR and 90-day
  artifact; clarified blank-column diagnostics and planned activation; corrected NFR-01 wording.
- Verified on `22d481b` plus the change: snapshot exit 0, 35 issues, counts Backlog 21 / Done 6 /
  In progress 2 / In review 2 / Ready 3. Branch tests covered creation, carry-over, post-squash
  rebuild and same-day rerun; actionlint 1.7.12 and YAML parsing passed. The review follow-up also
  pins `--force-with-lease` to the initially fetched SHA; `git diff --check` passes.
- Not verified: a GitHub schedule/dispatch or `github.token` fallback; the workflow cannot run
  until merged into `dev`. Latest actionlint rerun was unavailable because Docker was stopped.
- Decision: rebuild one bot branch from `dev` and lease against its fetched SHA so external writes
  fail safely. `BOARD_SNAPSHOT_TOKEN` remains account-holder TODO 8 (#103).

## 2026-09-18 - @limjiechao - T2-01 products list from the Claude Design Catalog artboards (#44, #93 PR-G)

- Kind: feature (UC1 browse) plus the first theme pass; artboards over plan and brief (note 39);
  react-perf rules off (note 37); no ADR, no NFR.
- Changed: catalog search schema, `Price`, `ProductCard`, the `/` route with loader and pager,
  the T2-02 placeholder route, the `?msw=1` browser seed, the BlocBuy theme overlay and Inter,
  `docs/design-screens.md` (artboard spec with provenance); plan steps 1–6, notes 36–39.
- Sources: `design-screens.md` §2/§3/§4.1 from the 2026-09-17 export of Claude Design project
  `27f5f1e0-…`; `BlocBuy Buyer Catalog.dc.html`, `blocbuy-theme.css`, `fig-tokens.css`.
- Verified on `cc1c11c` plus the change: `pnpm check` exit 0 (vitest 6 files / 18 tests, knip 0
  issues); `pnpm build` ok; `pnpm e2e` 1 passed, 2 skipped; MSW walkthrough screenshots at 390
  and 1280 (populated, page 2, category, empty), 0 page errors. Review follow-up `9582eb2`
  (CodeRabbit r4042311578/r4042311596): `msw=1` exact match, past-last-page redirect, route test
  (note 40); vitest 7 files / 21 tests, build and e2e green. Second follow-up (CodeRabbit
  r4043389594, raised on #115 against this card): the tile and the start link are `relative`, so
  the stretched title link no longer covers the whole page; card test added, `pnpm check` green.
- Not verified: the real backend, loading and error states in a browser, Inter offline,
  Safari 16.4.
- Problems: CR-11 no deal sizes on the card DTO (progress cue omitted); CR-15 `--muted` 4.41:1
  (used as designed); CR-10 category strings unknown; A2's CSP must allow Google Fonts. Open.

## 2026-09-18 - @limjiechao - T2-02 product detail with deal progress and not-found state (#44, #93)

- Kind: feature (PR-G, UC1 detail), frontend decisions recorded as plan §12 notes 41–42, no ADR
  (no new alternative was chosen over a settled one), no NFR.
- Changed: `frontend/src/features/catalog/{DealProgress,deal-time}.ts(x)` + tests;
  `src/routes/products.$productId.tsx` (replaces the T2-01 placeholder: `getListingOptions`
  loader with `queryClient.query` + `useSuspenseQuery`, artboard layout, gallery, trust triptych,
  skeleton, error and `notFoundComponent` states); `src/test/browser-msw.ts` (detail responses
  and a 404 for the sample listings, deal deadlines relative to page load); plan T2-02 steps 1–4
  ticked, §12 notes 41–42. Merged the T2-01 follow-up `9582eb2` (msw gate, last-page redirect,
  note 40) into this branch by fast-forward before the commit.
- Sources: `frontend/docs/design-screens.md` §4.2 and §4.5; raw artboards `BlocBuy Buyer Listing
  Detail.dc.html` (`open blocs`, `no open bloc`, `unavailable`, `loading`, `error` at 390 and
  1280) and `BlocBuy Buyer Errors.dc.html` (`Not found`) under `.claude/design-artboards/`
  (git-excluded, from the 2026-09-17 export of Claude Design project `27f5f1e0-…`). Artboards
  win over plan §4 T2-02 text (note 41). HeroUI prop names came from the installed 3.2.5 type
  definitions (`ProgressBar` root `value`/`maxValue`/`color`/`size`, `Track`/`Fill`; `Chip`
  `color`/`variant`/`size`), not the website: this session ran offline-first after the first
  attempt stalled on a network call.
- Verified (2026-09-18, worktree `product-detail` on `9582eb2` + this change): `pnpm vitest run
  src/features/catalog` 5 files / 22 tests; `pnpm check` exit 0 (gen:api 7 files; tsc clean;
  oxlint clean; oxfmt 43 files clean; vitest 9 files / 33 tests passed, including T2-01's new
  `routes/index.test.tsx`; knip 0 issues, 4 pre-existing config hints); `pnpm build` exit 0;
  `pnpm e2e` 1 passed, 2 skipped (dev-only). Walkthrough on `pnpm dev` + `?msw=1` with a
  throwaway Playwright script (`node_modules/.cache/t2-02-shots.mjs`): open deal (3 of 5, 2h 14m
  countdown, "Ends today"), urgent deal (4 of 5, 47:25, "Closing soon"), no bloc (solo price,
  "Be the first" card, "Start a bloc" without amount) and an unknown id (404 card) at 390 and
  1280, 0 page errors; PNGs `frontend/test-results/t2-02-{open-deal,urgent-deal,no-bloc,not-found}-{390,1280}.png`
  (not committed; `pnpm e2e` wipes the folder, re-run the script after it). **Not verified:**
  the real backend (no Docker; the backend's actual not-found shape is unknown, note 42);
  loading and error states in a browser (compile and follow the artboard geometry); the sticky
  bottom bar while scrolling (full-page screenshots freeze it at the viewport bottom); Intent
  skills not loaded (offline rule); the HeroUI website not consulted.
- Decisions: not-found mapping on `status === 404` or a `_404_` code (note 42); `Breadcrumbs`
  as plain router links rather than HeroUI `Breadcrumbs.Item` (no React Aria router provider,
  note 41c); seat dots capped at 10 seats; Join / Start a bloc / Buy alone rendered as designed
  with no handlers until T2-04 and UC3/UC4.
- Problems / questions for the backend and design owners: CR the contract declares no 404 for
  `getListing` and no product-domain error code exists; CR `ActiveDealResponse` has no starter,
  number or per-bloc list (the artboard's three named blocs), so one "Open bloc" row renders; CR
  no listing status on `ProductDetailResponse`, so the "unavailable" artboard cannot render; the
  artboard's "My blocs" exit on the 404 card waits for the account route. Status: open, not
  blocking.
- Next: user pushes `feature/frontend-product-detail` (stacked on #114 → `dev`); then T2-03
  (filter chip row in the T2-01 slot; artboards draw a single-select `Chip` row and no keyword
  field, plan §12 will need the correction the handoff asks for).
