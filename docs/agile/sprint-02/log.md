# Sprint 2 log (2026-09-14 to 2026-09-25)

Goal: track the `[T1] Sprint 2` container issue #93 (`frontend/docs/plan.md` T1-02). Tier 1 targets the week ending 2026-09-20, tier 2 the week ending 2026-09-25; code freeze 2026-09-24 EOD.

One entry per unit of work per member, newest at the bottom. Shape and rules: `.claude/skills/agile-evidence-capture/references/templates.md`. Three to eight lines each. Say what was verified and what was not.

## 2026-09-16 - @limjiechao - Evidence conventions and agile-evidence-capture skill

- Kind: ops, decision
- Changed: `.claude/skills/agile-evidence-capture/`, `.claude/settings.json` (commit hook), `docs/agile/`, `docs/quality/nfr-register.md`, `.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`, `frontend/docs/plan.md` (sprint log moved repo-wide, skill renamed)
- Verified: hook script exercised with sample commits (NFR config without evidence -> checklist; `style:` and `Evidence-skipped:` -> silent; enforce mode -> exit 2); snapshot script run against the live board into a scratch directory. Skill evaluated on four task prompts (login rate limiting, pin actions by SHA, UC1 `savingsPercent`, sprint close) in throwaway clones, each run with and without the skill and graded against 27 assertions: 27/27 with, 15/27 without; code quality equal in both, the difference is entirely the evidence artefacts. Not yet exercised by a teammate's session.
- Decisions: ADR `frontend/docs/adr/0008-documentation-and-evidence.md` (Accepted): sprint log and NFR register are repo-wide; ADRs stay a frontend practice, the backend keeps its D-table and design docs, cross-cutting decisions live in the owning `docs/` page
- Problems: Sprint 1 report shipped with TODO burndown and contribution cells; NFR changes (#84, #75, #45) had no issue or doc trail. Action: this change set. Status: in review.
- Next: file the Sprint 2 container issue (T1-02), create `nfr:*` labels, first board snapshot.

## 2026-09-16 - @limjiechao - T1-01 dev CloudFront day-1 probe (#44)

- Kind: ops
- Changed: this log only (zero code). Plan task T1-01 in `frontend/docs/plan.md`.
- Verified: against `https://d1b3np88z657cp.cloudfront.net` at about 13:00 UTC, health probe run three times with the same result. Script: three `curl` probes from the T1-01 task row plus the root-headers check.

| Probe | Result | Meaning |
|---|---|---|
| GET /api/actuator/health | `503 text/html`, `server: awselb/2.0`, `x-cache: Error from cloudfront` (3 of 3 runs) | `/api/*` behavior reaches the ALB; the ALB has no healthy target. Expected `200 application/json` |
| GET /api/v1/products?page=1 | `503 text/html` | same |
| GET /products/probe | `403 application/xml` (S3 OAC AccessDenied) | A1 SPA fallback attached: no |
| GET / and /index.html | `403 application/xml` | no frontend build in the bucket yet (expected before T1-14) |
| GET /api/v1/auth/oauth2/google | `503`, no `Location`, no `Set-Cookie` | B3 (Secure flag) and B5 (redirect_uri) evidence not obtainable today |
| GET / headers | CSP absent; HSTS absent | A2 absent |

- Problems: dev backend is down. The latest `ci-backend` run on `dev` (merge of #92, 2026-09-16T12:23Z, run 35095495827) failed at job `deploy-existing-ec2` after `publish-ecr-dev` succeeded. Action: raised with @l0sgAi and @PPsyrius; re-run the OAuth probe once dev is healthy and append the B3/B5 rows here. Status: closed 2026-09-17, rows in the tier-1 close-out entry below.
- Decisions: none. Note for T2: `gh auth status` shows `read:project` on the current token and `gh project view 2 --owner PPsyrius` lists 21 items, so the board is readable at filing time.
- Next: T1-02 file the 13 blocker issues; T1-04 runtime pins.

## 2026-09-16 - @limjiechao - T1-02 Sprint 2 container and blocker issues filed (#93)

- Kind: ops
- Changed: GitHub only. Container #93. Backend B1–B5 = #94–#98 (@l0sgAi). AWS A1–A3 = #99–#101 (@l0sgAi, @PPsyrius), A4 = #102 (@PPsyrius). Team T2–T4 = #103–#105 (@PPsyrius). Bodies are `frontend/docs/plan.md` §10 verbatim plus a "Sprint 2 container issue: #93" line; #103 carries a filing-time note that `read:project` is already granted. The T1-01 probe result and the failed dev deploy are posted as a comment on #93.
- Verified: the T1-02 task-row count command prints 13.
- Problems: none.
- Next: T1-03 runbook PR (PR-B) so #99–#101 have steps to execute; T1-05 scaffold.

## 2026-09-16 - @limjiechao - T1-04 runtime and package-manager pins (#44)

- Kind: chore, nfr (NFR-17 maintainability: reproducible toolchain; row added after the commit hook asked for it)
- Changed: `frontend/.mise.toml`, `frontend/.nvmrc`, `frontend/.npmrc`, `frontend/package.json` (`packageManager`, `engines`, empty `scripts`), root `.gitignore` (generated client, Playwright outputs, e2e auth state).
- Verified: `corepack pnpm --version` in `frontend/` prints `12.4.2` (binary downloaded on first use); `node --version` prints `v24.14.1` (nvm). Not verified: `mise install`, mise is not installed on this machine.
- Decisions: ADR 0013 (stub today; body lands in T1-15).
- Next: T1-05 scaffold.

## 2026-09-16 - @limjiechao - T1-03 frontend runbook steps and policy examples (#93, PR-B)

- Kind: ops, nfr (NFR-04 OIDC deploy role reused for the frontend; NFR-16 CSP/HSTS policy source now versioned, row stays `documented` until A2 executes in T2-11)
- Changed: `docs/deploy/dev-cloudfront-deployment.md` (§7.3 marked superseded, §10 npm to pnpm with the CI-identical release commands, new §14 SPA fallback, §15 response headers, §16 OIDC deploy role, §17 `frontend-dev` environment; each section links its blocker A1 #99, A2 #100, A3 #101, A4 #102); `deployment/frontend/cloudfront/spa-fallback.js`, `deployment/frontend/cloudfront/response-headers-policy.json`, `deployment/frontend/iam/README.md` and the two `.json.example` policies. Content is plan §8 verbatim plus the issue links.
- Verified: `python3 -m json.tool` passed on all three JSON files; `node --check deployment/frontend/cloudfront/spa-fallback.js` passed. Not verified: no AWS commands run (account-holder steps; backend down, so §14/§15 probes can't run yet).
- Decisions: implements ADRs 0002/0004/0005/0007; bodies land in T1-15.
- Problems: OIDC `sub` format unconfirmed (backend example has id-suffixed subject) — §16 tells the operator to copy whichever form works. Open until A3 executes.
- Next: user opens PR-B (@l0sgAi runbook, @PPsyrius `deployment/`); A1–A4 executed by the account holder; T1-05 scaffold.

## 2026-09-16 - @limjiechao - T1-05 Vite 8 / React 19 / TanStack Router / Tailwind 4 / HeroUI scaffold (#44, #93)

- Kind: feature (scaffold), nfr (NFR-17 row extended with the lockfile; `package.json` and `vite.config.ts` are toolchain, not a new NFR)
- Changed: `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/index.html`, `frontend/src/{main,router}.tsx`, `frontend/src/styles/app.css`, `frontend/src/routes/{__root,index}.tsx`, `frontend/src/api/query-client.ts` (one-line stub, replaced in T1-07), `frontend/src/routeTree.gen.ts` (generated, committed read-only, plan §12.16), `frontend/package.json` (23 exact pins from plan §1, scripts `dev`/`build`/`preview` only), `frontend/pnpm-lock.yaml`. File contents are plan §4 T1-05 steps 2–10 verbatim.
- Verified: `pnpm install --frozen-lockfile && pnpm build && test -f dist/index.html && ls dist/assets/*.js` on `feature/frontend-scaffold-t1-05`: lockfile current, 240 modules in 548 ms, `dist/index.html` + 4 JS chunks + 435 kB CSS + 2 fonts. Headless Chrome vs dev server and `vite preview`: `/` and `/does-not-exist` render, 0 page errors (one 404 for absent favicon). Not verified: `mise install`; `/api` proxy (backend down); `tsc --noEmit` (fails today, see Problems).
- Decisions: ADR 0001 (stack and CSR; `routeTree.gen.ts` committed), ADR 0003 (generated client gitignored, no `src/api/generated` yet), ADR 0013 (exact pins, `pnpm-lock.yaml` frozen in CI). No new ADR: every file is as planned.
- Problems: (1) `pnpm exec tsc --noEmit` fails with two `TS2688` errors because `tsconfig.json` names `vitest/globals` and `@testing-library/jest-dom/vitest`, which arrive in T1-08 and T1-11. Action: recorded as plan §12.18, tsconfig kept as written, T1-11 must show `tsc` green before PR-C. Status: open until T1-11. (2) `pnpm add` reformatted `engines` in `package.json` onto three lines; cosmetic, left as pnpm wrote it. (3) `index.html` has no favicon; cosmetic and still open after T2-04.
- Next: T1-06 hey-api client generation (separate session, branch to be cherry-picked onto `feature/frontend-scaffold`).

## 2026-09-16 - @limjiechao - T1-06 generated API client (hey-api openapi-ts) (#44)

- Kind: feature
- Changed: `frontend/openapi-ts.config.ts`, `frontend/src/api/hey-api.ts`, `frontend/package.json` (`gen:api`/`prepare` scripts, `build` → `pnpm gen:api && vite build`, `@hey-api/openapi-ts` and `msw` exact pins), `frontend/pnpm-lock.yaml`, `frontend/pnpm-workspace.yaml` (new, `allowBuilds.msw: true`).
- Verified: `pnpm gen:api` produces 7 files under `src/api/generated` (gitignored, confirmed with `git check-ignore`); the task-row command passes; `pnpm build` succeeds.
- Decisions: `@hey-api/openapi-ts` repinned to the exact prerelease `0.0.0-next-20260824173136`, not the brief's `0.99.0` — `0.99.0` uses the classic TypeScript compiler API, which `typescript@7.0.2` no longer ships (upstream hey-api/hey-api#4235, confirmed open); `frontend/pnpm-workspace.yaml` committed so `pnpm install --frozen-lockfile` doesn't fail non-interactively on msw's postinstall. ADR 0003 addendum; plan §12 notes 19, 28.
- Problems: brief's `0.99.0` pin crashed `gen:api`; pnpm-level workarounds failed before the prerelease repin resolved it. `msw.gen.ts` exports `createMswHandlers().all()`, not `getAllMocks` — flagged to T1-07/T1-08/T1-11.
- Next: T1-08 quality toolchain and the `pnpm check` gate.

## 2026-09-16 - @limjiechao - T1-08 quality toolchain and the `pnpm check` gate (#44)

- Kind: feature, nfr (NFR-18 maintainability: the `pnpm check` gate; row added)
- Changed: `frontend/.oxlintrc.json`, `frontend/.oxfmtrc.json`, `frontend/knip.json`, `frontend/vitest.config.ts`, `frontend/src/test/{setup,msw}.ts`, `frontend/scripts/ts-nocheck-generated.mjs`, root `lefthook.yml`, `frontend/package.json` (scripts and devDependencies), `frontend/tsconfig.json`.
- Verified: `pnpm check` (`gen:api` → `tsc --noEmit` → `oxlint --type-aware` → `oxfmt --check` → `vitest run` → `knip`) exits 0; `pnpm build` passes; each step also verified standalone during implementation.
- Decisions: kept `exactOptionalPropertyTypes: true` project-wide and post-processed `src/api/generated/**` with `// @ts-nocheck` instead of relaxing the flag (ADR 0003 addendum); `react/react-in-jsx-scope` off (obsolete under the automatic JSX runtime); `import/no-unassigned-import` kept at error with an allow-list for `**/*.css` and `@testing-library/jest-dom/vitest` (ADR 0006 addendum); oxfmt and lefthook exclude `docs/**` and `README.md`; the `e2e` script is deferred to T1-13; knip `ignoreDependencies` temporarily carries `@tanstack/react-form` and `@testing-library/user-event`; `allowImportingTsExtensions` was tried then removed as unnecessary. Plan §12 notes 20, 22-25, 29.
- Problems: the first `oxfmt` write run reformatted `frontend/docs/*.md` (1130 lines in `plan.md`), `docs/globals.css` and `frontend/README.md`; restored and excluded prose from the formatter. The initial change left `tsc --noEmit` red on 29 pre-existing `exactOptionalPropertyTypes` errors in `src/api/generated/**`; fixed before review. Task review found two lint-rule choices needing justification, not weakening; both were documented.
- Next: T1-07 envelope unwrap, `ApiError`, 401 interceptor, `QueryClient` defaults.

## 2026-09-16 - @limjiechao - T1-07 result envelope unwrap, ApiError, 401 interceptor, QueryClient defaults (#44, #42)

- Kind: feature
- Changed: `frontend/src/api/envelope.ts`, `frontend/src/api/envelope.test.ts`, `frontend/src/api/client.ts`, `frontend/src/api/query-client.ts` (replaces the T1-05 stub).
- Verified: `pnpm vitest run src/api` RED (module missing) → GREEN 3/3; `pnpm check` exits 0; `pnpm build` passes.
- Decisions: `OK_CODE = 'COMMON_200_000'` (backend `CommonErrorCode.SUCCESS`), not the brief's `'0'` (ADR 0009 addendum); `OK_CODE` kept module-private and `client.ts` drops the brief's `export { client }` — both were unused exports per knip, nothing consumes them yet. Plan §12 note 26; plan's T1-07 "Interfaces produced" line corrected to `installAuthInterceptor(queryClient)` to match the brief's own step-4 code, which never took `client` as a parameter.
- Problems: none blocking; three narrow oxlint-driven rewrites (an unsafe-cast avoidance in `unwrapResult` and in the interceptor's response-body parsing, a conditional-expect restructure in the test) with no behavior change, see the task report for detail.
- Next: T1-11 first component test (Header).

## 2026-09-16 - @limjiechao - T1-11 first component test: Header (#42)

- Kind: feature
- Changed: `frontend/src/components/Header.tsx`, `frontend/src/components/Header.test.tsx`, `frontend/src/routes/__root.tsx` (mounts `<Header />` above `<Outlet />`), `frontend/knip.json` (`ignoreDependencies` trimmed of `@heroui/react`, `@testing-library/react`).
- Verified: `pnpm vitest run src/components/Header.test.tsx` RED (missing module, then a router-context error) → GREEN 1/1; `pnpm check` exits 0, vitest 4/4 overall (3 envelope tests, 1 Header test); `pnpm build` passes. This closes the `tsc` TS2688 problem recorded in the T1-05 log entry above.
- Decisions: the login control is HeroUI's `Link` primitive with `buttonVariants({ variant: "primary" })`, not `Button as="a"` — `Button` wraps react-aria-components' `Button`, which has no `href`/`as` (confirmed against the installed `.d.ts`); the test wraps a minimal memory-history `RouterProvider` since `Link` (TanStack Router) throws outside one. Plan §12 note 27.
- Problems: none blocking. Not verified: the interceptor's `removeQueries` on the actively observed `me` query can cause one extra `/auth/me` request per later re-render after a 401 (bounded, not a loop). Status: fixed in the whole-branch review below, before T2-04.
- Next: PR-C (T1-06..T1-11, plus T1-09/T1-10) not yet opened; branch `feature/frontend-t1-06-11` unpushed.

## 2026-09-17 - @limjiechao - Whole-branch review fixes for T1-06..T1-11 (#44, #42)

- Kind: fix, test
- Changed: landed the T1-06..T1-11 evidence (sprint log, NFR-18, ADR notes, plan ticks); made `lefthook.yml` exclude globs repo-root relative; fixed 401 handling and added client/Header tests plus Vitest/knip updates.
- Verified: `pnpm check` (`gen:api` → `tsc --noEmit` → `oxlint --type-aware` → `oxfmt --check` → `vitest run` → `knip`) exits 0; `vitest run` 3 files / 7 tests passed; `pnpm build` passes. `lefthook run pre-commit` verified twice by hand: docs-only staged ⇒ `frontend-format` skips (no files), exit 0; a real `frontend/*.ts` change staged ⇒ the file reaches oxfmt/oxlint/tsc as before.
- Decisions: on 401 the interceptor now `setQueryData(meQueryKey(), { code: OK_CODE, message: 'anonymous' })` instead of `removeQueries` — the observed `me` query stays settled (verified: `state.data` survives the fetch's own subsequent error dispatch) instead of hanging pending and refetching on every root re-render; never `invalidateQueries`/`resetQueries` there since the `me` request's own 401 would loop. `OK_CODE` re-exported from `envelope.ts` for this consumer. lefthook's `exclude` globs are matched against repo-root-relative paths even under a command's `root:`, so `docs/**`/`README.md` etc. had to become `frontend/docs/**`/`frontend/README.md` to actually filter files under `root: "frontend/"`.
- Problems: parked, not fixed here — TanStack Query devtools ship in the production bundle and `sourcemap: true` publishes full source maps (both pre-existing from T1-05); needs a plan decision on an `import.meta.env.DEV` carve-out (dead-code elimination only) or a lazy import, before T1-14.

## 2026-09-17 - @limjiechao - T1-09 TanStack query/router lint rules spike (#44)

- Kind: chore (tooling), nfr (maintainability, NFR-18)
- Changed: `frontend/.oxlintrc.json` gains `jsPlugins` and the four `@tanstack/*` rules; `frontend/package.json` and lockfile add exact query/router plugin pins; ADR 0006, plan T1-09, this log and NFR-18 evidence are updated. No new NFR row: T1-09 only widens the existing gate.
- Verified: substituted fixtures (the plan's own fixture doesn't trigger a TanStack rule — see Decisions): query fixture fails with `@tanstack/query(exhaustive-deps)` missing-`id`, router fixture fails with `create-route-property-order` — both exit 1 as required. `pnpm check` exit 0 (vitest 3 files / 7 tests); `pnpm lint` clean on real sources. Exact fixture commands are in plan §12 note 30.
- Decisions: the **oxlint JS-plugins** path won, so step 2 of the plan (a minimal ESLint carrying `eslint` 10.10.0 + `eslint-plugin-oxlint` 1.83.0 + `frontend/eslint.config.js`) was not needed and no ESLint is configured or invoked; `lint` stays `oxlint --type-aware` and the gate keeps one linter. Caveat: `eslint@10.10.0` appears transitively (peer auto-install) — never direct, never run; ADR 0006 carries the precise wording. Both plugins loaded first try under Node 24, rule ids namespaced as planned; knip does not flag them. Cost: `oxlint --type-aware` 0.246 s → 0.310 s. Recorded as §12 note 30 + ADR 0006 (path, cost, alpha risk).
- Problems: the plan's task-row fixture can never pass its own criterion — it fails on `rules-of-hooks` (bad name `x`), not a TanStack rule. Substituted as above (§12 note 30).
- Next: T1-10 agent configuration; PR-C not yet opened.

## 2026-09-17 - @limjiechao - T1-10 Agent configuration (#44)

- Kind: ops/process, decision (ADR 0012)
- Changed: `frontend/AGENTS.md`, `frontend/CLAUDE.md` (`@AGENTS.md`), root `.claude/settings.json` (merged, not overwritten), `.claude/hooks/frontend-{fix,typecheck,bootstrap}.sh`, `frontend/package.json` (`intent` key), `frontend/docs/tanstack-skills.json`, `frontend/.oxfmtrc.json`, `lefthook.yml` (formatter ignores for the Intent-managed files), ADR 0012 + `frontend/docs/adr/README.md`, plan §11/§12 and the T1-10 boxes, this log, NFR-18 evidence.
- Verified: **Intent discovery.** `pnpm dlx @tanstack/intent@0.4.0 list`, `install`, and `install --map`; `list --json` found router-core 1.171.30 (10 skills, transitive under pnpm) + router-plugin 1, so no direct dependency was added. With the `@tanstack/*` allowlist + 3 excludes the committed manifest holds 11 skills; the corrected jq filter exits 0. **Hook simulations** (piped stdin): fix reformats a mangled line, typecheck exits 2 on a `TS2322` probe and 0 when clean, bootstrap regenerates a deleted client idempotently. **Review round 1:** `jq` degrades to no-op when absent; deny rules gained exact-match siblings; `.env` reads widened; fix-hook gained relative-path normalisation. All simulations re-run green. **Gate.** `pnpm check` exit 0 (vitest 3 files / 7 tests; knip only pre-existing hints).
- Decisions: ADR 0012 (Accepted). Hooks scoped to `frontend/**` via `if` + in-script checks; root settings merged so the evidence hook survives; `router-core/ssr` excluded; manifest paths repo-relative for the T1-12 `intent-stale` diff. Plain `git push` stays out of `deny` deliberately; refspec/trailing-`--force` spellings fall through to a prompt.
- Problems: (1) in-session hook smoke (step 5) needs a real Claude Code session at repo root — owed, step unticked. (2) bootstrap false positive (warm `node_modules` skips `prepare`) fixed to run `gen:api` explicitly; worktree `.git` check and `lefthook.checksum` grep fixed. (3) `AGENTS.md`/`CLAUDE.md` added to oxfmt/lefthook ignores (Intent block must stay byte-identical). All are plan §12 note 31.
- Next: T1-13 Playwright smoke. PR-C and PR-D not opened.

## 2026-09-17 - @limjiechao - T1-13 Playwright smoke (#44)

- Kind: test (NFR: testability, ADR 0011)
- Changed: `frontend/playwright.config.ts`, `frontend/e2e/smoke.spec.ts`, `frontend/package.json` (`e2e` script, `@playwright/test` devDependency), `frontend/pnpm-lock.yaml`, `frontend/AGENTS.md` (Commands bullet), `frontend/docs/adr/0011-testing-strategy.md` (Consequences), `frontend/docs/plan.md` (T1-13 boxes, §12 note 32), `docs/quality/nfr-register.md` (NFR-18 evidence), this log.
- Verified: `corepack pnpm install --frozen-lockfile`; `pnpm exec playwright install --with-deps chromium`; `pnpm build && pnpm e2e`; `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e`; `pnpm check`. Install generated 7 files with 19/19 `// @ts-nocheck`; build took 616 ms. Local e2e: 1 passed / 2 skipped. Dev: 2 failed / 1 skipped (root 403, health 503) and produced `trace.zip`. Gate passed with vitest 3 files / 7 tests and knip only pre-existing hints.
- Decisions: no new ADR; ADR 0011 Consequences updated. One deviation: the brief's `webServer: cond ? undefined : {...}` fails `tsc` under `exactOptionalPropertyTypes` (`TS2769`); rewritten to omit the key instead — same runtime. `e2e` stays out of `pnpm check` (slower, needs `dist/`; note 24).
- Problems: dev CloudFront unusable today (root 403 until T1-14, health 503 backend down) — the dev-target e2e rerun is **owed**. A1 (#99) open, so the deep-link test stays `fixme`'d per T1-13.
- Next: T1-12 `.github` PR-E (needs @PPsyrius), T1-14 first deploy. PR-C, PR-D and PR-F not opened.

## 2026-09-17 - @limjiechao - T1-12 .github frontend CI pipeline (#44, #104, #105, PR-E)

- Kind: nfr, ops
- Changed: `.github/workflows/ci-frontend.yml` (new: `detect-changes`, `frontend-check`, `intent-stale`, `e2e-preview`, `deploy-s3-dev`, `e2e-dev`), `.github/workflows/security.yml` (frontend path filters, pnpm-lockfile Trivy scan + SARIF), `.github/dependabot.yml` (npm `/frontend`), `.github/CODEOWNERS` (§7.4 block, `/.github/` anchor), `CONTRIBUTING.md` (required checks, Frontend subsection), `frontend/docs/plan.md` (T1-12 boxes 1–4, §12 note 33), `docs/quality/nfr-register.md` (NFR-17/18 evidence, new NFR-19), this log. `.gitignore` (§7.5) and the PR template (§7.6) already satisfied the plan; untouched.
- Verified: actionlint 1.7.12 twice (release binary + the `security.yml`-pinned Docker digest) — both exit 0, all five workflows. `pnpm dlx yaml@2.9.1 valid` passed for five workflows + `dependabot.yml`. `intent-stale` dry-run: manifest diff clean, marker grep exit 0. `import.meta.env` grep: no hits. pnpm/setup-node path inputs confirmed against pinned `action.yml`. `pnpm check` not re-run (no `frontend/` source touched). **Owed:** first GitHub run (`frontend-check` green on PR-E).
- Decisions: cross-cutting, no ADR; shape per plan §7 and ADRs 0004 (build once, promote the same `dist/` to S3), 0008 (evidence), 0013 (toolchain pins, now enforced in CI), bodies in T1-15. One required check (`frontend-check`) with `no-runtime-env` folded in as a step; `deploy-s3-dev` gated on `ENABLE_S3_DEPLOY` until A3/A4 (#101, #102) land. Deviations: plan §12 note 33.
- Problems: `corepack pnpm install --dir frontend` ran pnpm 10 (NFR-17 `engines` correctly rejected it — run from `frontend/`). Dependabot pnpm-12 support undocumented — "Check for updates" after first merge. PR-E, review, branch-protection ask and the five `frontend-dev` variables are user/@PPsyrius-owned.
- Next: user opens PR-E; T1-14 first deploy once A3/A4 close; T1-15 ADR bodies (next entry, same branch).

## 2026-09-17 - @limjiechao - T1-15 ADR 0001–0013 bodies and blocker table (#93)

- Kind: decision, ops
- Changed: `frontend/docs/adr/0001..0013-*.md` (Context, Decision, Consequences written; Status `Accepted`; 0008 and 0012 unchanged), `frontend/docs/adr/README.md` (Status column), `frontend/docs/plan.md` (T1-15 steps 1–4 ticked, §12 note 34), this log.
- Verified: `grep -L '^## Decision' frontend/docs/adr/0*.md | wc -l` printed 0; `wc -l frontend/docs/adr/0*.md` showed every ADR under 60 lines; the plan §12-note-34 awk check counted 13 Accepted ADRs. Facts checked against the plan, configs, contract and backend code. Not run: AWS, backend, `pnpm`.
- Decisions: all thirteen Accepted. Changed-since-§9 facts: 0001 (`routeTree.gen.ts` committed; T2-09 slot), 0002 (`setQueryData` interceptor; B1/B3 open), 0003 (`next` pin, `// @ts-nocheck`, `createMswHandlers`), 0004 (devtools/sourcemap parked pre-T1-14), 0006 (jsPlugins won; transitive eslint), 0009 (`OK_CODE` real value), 0010 (Babel carrier; T2-08 slot), 0011 (e2e outside check; dev owed), 0013 (Dependabot in T1-12; pnpm-12 unverified). 0005/0007 match §9.
- Problems: agent was worktree-pinned with no shell — authored in scratch, orchestrating session applied it on `feature/frontend-t1-12`; `feature/frontend-t1-15` redundant. Bodies re-checked against tree; all twelve blockers open per `gh issue list`.
- Next: user opens PR-E from this branch; T1-14 first deploy after A3/A4; T2-08 addendum for 0010; T2-09 addendum for 0001; T2-11 CSP addendum for 0002.

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
- Changed: `frontend/src/components/Header.tsx`, `frontend/src/components/Header.test.tsx` (cherry-picked B4 fix: render `nickname` with a `userId` fallback, test id `session-user`); `frontend/package.json` (`hooks:install` script), `frontend/AGENTS.md` (git-hooks line), this log.
- Verified: CI `frontend-check` run `35229148035`: tsc/oxlint/oxfmt/vitest (3 files / 8 tests) pass; knip flags unused `lefthook` (plugin only looks in knip cwd or installed hooks — config is at root, CI has no hooks). Fix: `"hooks:install": "lefthook install"` script reference; `pnpm check` exit 0 locally.
- Decisions: a real script reference over `knip.json#ignoreDependencies`, so the dependency stays checked and the install command is documented in one place; AGENTS.md now says `pnpm hooks:install`. No ADR change (ADR 0006 already owns knip); NFR-18 unchanged.
- Problems: the knip failure was masked on every earlier CI run because `tsc` failed first on `loginId`, so the gate had never reached knip on GitHub before this commit.
- Next: user pushes; watch `frontend-check`; the `frontend-check` always-report change and the Trivy pnpm scan restore from the local rebase branch are still to be replayed.

## 2026-09-17 - @limjiechao - PR #106 review: stale `MeResponse` docs after B4 (#97, #44)

- Kind: docs (review fix, CodeRabbit r4030090764)
- Changed: ADR 0002 Context (`/auth/me` shape) and Consequences (header display), `blueprint.md` networking bullet (dated correction) and blocker 4, plan §1 auth line and the B4 blocker row.
- Verified: `MeResponse` is `{userId, nickname, avatarUrl}` on dev and branch (PR #89); `Header.tsx` renders `nickname ?? userId`. ADR 0002 stays 59 lines. No code change.
- Decisions: blueprint keeps its original wording with a dated correction (it is a snapshot); the plan and the ADR say the current contract outright. `blueprint-review.md` rows and the B4 issue draft in plan §12 are left as written: they describe the state on the day.
- Next: T2-04 renders `avatarUrl`; #97 closure is on the backend owner.

## 2026-09-17 - @limjiechao - PR #106 review: evidence hook honours `git -C <dir> commit` (#44)

- Kind: fix (agent tooling), review fix (CodeRabbit r4030090657)
- Changed: `.claude/skills/agile-evidence-capture/scripts/commit-evidence-hook.sh`: the matcher already accepted `git -C <dir> commit`, but `rev-parse`, `diff --cached` and `status` ran against the hook `cwd`. The `-C` path is now extracted once (quotes stripped, `~` and relative paths resolved against cwd) into `repo_dir`, and the three git calls use it. Commands without `-C` keep `repo_dir = cwd`.
- Verified: six payloads (temp repo, staged `src/x.ts`) against old + new script: absolute/relative/quoted `-C` now report "Sprint log" missing (old: only "Issue reference"); plain commits unchanged; enforce mode exits 2; `git -C /x status` passes through. `bash -n` clean; shellcheck unavailable.
- Decisions: resolve `-C` rather than stop matching it, so worktree-style commits from a different cwd still get the evidence nudge. Not an NFR (agent-side advisory hook).
- Next: none; the hook's enforce mode remains opt-in.

## 2026-09-17 - @limjiechao - PR #106 review: exact package-manager allowlist in `.claude/settings.json` (#44)

- Kind: security (agent permissions), review fix (CodeRabbit r4030090644, CWE-78)
- Changed: root `.claude/settings.json` `permissions.allow`: `Bash(pnpm *)`, `Bash(corepack *)` and `Bash(mise *)` replaced by 27 exact commands (repo scripts, the four `pnpm exec` gate tools, pinned `pnpm dlx @tanstack/intent@0.4.0` list/install forms, `corepack enable` / `pnpm --version` / `install --frozen-lockfile` / `check`, `mise install`); plan §12 note 31(i).
- Verified: allow-list is 42 entries (27 exact + 15 git/gh); `deny`/hooks/keys byte-identical. The 27 cover every pnpm/corepack/mise invocation in AGENTS.md, plan §6, hooks and CI except deliberate prompts.
- Decisions: exact strings, no wildcards, as the review asked; `pnpm add`, `pnpm exec playwright install`, `pnpm dev`/`preview` and the Intent block's `pnpm dlx @tanstack/intent@latest load …` prompt on first use. `pnpm publish` stays in `deny`. Not a runtime NFR (agent-session permission surface); ADR 0012 unchanged, plan note 31(i) records it.
- Next: none.

## 2026-09-17 - @limjiechao - PR #106 review round r5236778340: thirteen CodeRabbit findings, one commit each (#44)

- Kind: review fix (CI, runbook, ADR, design brief, plan, agent skill, Playwright)
- Changed, in commit order: `security.yml` regains the frontend deps filter + Trivy pnpm scan the rebase dropped (r4037739726); runbook §14 + plan §7 `create-function` fixes (`Runtime` in `--function-config`, no `Autopublish`, publish with returned ETag); runbook §16 decodes the OIDC `sub` from a real token; ADR 0007 `sensitive`/state-controls wording; skill templates `YYYY-MM-DD` + repo-relative script path; design brief source → `blueprint.md`; blueprint-review `loginId` rows marked historical; accent-fill rule decided (below); per-team joins → Tier 2; plan T1-06 synced to shipped generator pin/`createMswHandlers`; plan-session B4 draft off `loginId`; Playwright builds `dist/` when missing + AGENTS/CONTRIBUTING updates.
- Verified: `security.yml` diff byte-identical to actionlint-clean T1-12 blob; Playwright builds `dist/` when missing (1 passed/2 skipped) and skips build when present; oxfmt/oxlint/tsc green; OIDC decode tested on sample JWT; `loginId`/`getAllMocks`/`Autopublish` greps after each docs edit. Not on GitHub: `trivy-deps` fires only once a frontend lockfile change is pushed.
- Decisions: accent fill owned by team price, team progress, one primary action per screen (owner-confirmed; header sign-in stays neutral → T2-04 restyle item); Tier-1 detail shows one Join, per-team list is Tier 2; day-of snapshot docs keep wording with dated notes; each fix commit carries `Evidence-skipped` pointing here.
- Problems: stray `frontend/dist.bak` left in tree — remove by hand.
- Next: push + reply on threads; ask @PPsyrius whether frontend Trivy stays in `trivy-deps` or becomes its own job.

## 2026-09-17 - @limjiechao - Tier 1 close-out after PR #106: dev evidence, `frontend-check` required-check shape, Dependabot guard (#93, #104, #44)

- Kind: ops and NFR-01/16/19; CI shape recorded in `ci-frontend.yml` and plan note 35.
- Changed: restored the #108 every-PR `frontend-check`, ignored incompatible `openapi-ts` releases, updated the three NFRs and plan status, and captured `2026-09-17.csv` (35 issues).
- Verified: health/products 200; `/` and probe 403; OAuth start 302 with correct callback, state cookie `HttpOnly; SameSite=Lax`, not `Secure`. Dev e2e 1 passed / 1 failed / 1 fixme. Delivery-disabled push run `35239337645` green; `trivy-deps` run `35238582370` green; actionlint/Dependabot-YAML/tsc pass.
- Dependabot opened #111/#112 (pnpm 12 parses) but both hit hey-api#4235 under TS 7. GHSA-hhx9-57xq-r5rw is a bad version-ordering match: the pinned snapshot postdates 0.97.3 with its fix.
- Not verified: the first backend-only "not applicable" run, automatic closure of #111/#112, or frontend deployment. Add `frontend-check` to `dev-require-review` only after this PR merges.
- Problems: T1-14 remains blocked by A3 #101/A4 #102; B3 #96 remains unmet; #94/#95/#97 landed in PR #89 but still need closing.

## 2026-09-18 - @limjiechao - PR #113 review follow-up: scheduled board snapshot (#113, #103, #93)

- Kind: ops/process and NFR-01 documentation; design and rejected alternatives are in the CI plan; no ADR.
- Changed: added the daily/manual workflow, rolling `metrics/board-snapshots` PR and 90-day artifact; clarified blank-column diagnostics and planned activation; corrected NFR-01 wording.
- Verified: snapshot exit 0, 35 issues, counts Backlog 21 / Done 6 / In progress 2 / In review 2 / Ready 3. Branch tests covered creation, carry-over, post-squash rebuild and same-day rerun; actionlint 1.7.12 and YAML parsing passed. The review follow-up also pins `--force-with-lease` to the initially fetched SHA; `git diff --check` passes.
- Not verified: a GitHub schedule/dispatch or `github.token` fallback; the workflow cannot run until merged into `dev`. Latest actionlint rerun was unavailable because Docker was stopped.
- Decision: rebuild one bot branch from `dev` and lease against its fetched SHA so external writes fail safely. `BOARD_SNAPSHOT_TOKEN` remains account-holder TODO 8 (#103).

## 2026-09-18 - @PPsyrius - Independent Frontend SonarCloud quality gate (#104, #93)

- Kind: NFR-20 maintainability and CI.
- Changed: split backend/frontend SonarCloud jobs; frontend scans `frontend/src` with LCOV and keeps a `dev` baseline. Synced badges and quality docs; checkouts do not persist GitHub credentials, while change detection fetches history during checkout rather than requiring a later fetch.
- Verified: YAML, Actionlint and `git diff --check` pass; zizmor has no medium/high findings; focused coverage passed 5/5; PR #123's frontend quality gate passed.
- Decision: separate projects share `SONAR_TOKEN`; CodeQL remains unchanged.
- Problem: malformed local `@internationalized/date` blocked full coverage; Linux CI passed.
- Next: require both renamed checks; merge establishes the frontend `dev` baseline.

## 2026-09-18 - @limjiechao - T2-01 products list from the Claude Design Catalog artboards (#44, #93 PR-G)

- Kind: feature (UC1 browse) plus the first theme pass; artboards over plan and brief (note 39); react-perf rules off (note 37); no ADR, no NFR.
- Changed: catalog search schema, `Price`, `ProductCard`, the `/` route with loader and pager, the T2-02 placeholder route, the `?msw=1` browser seed, the BlocBuy theme overlay and Inter, `docs/design-screens.md` (artboard spec with provenance); plan steps 1–6, notes 36–39.
- Sources: `design-screens.md` §2/§3/§4.1 from the 2026-09-17 export of Claude Design project `27f5f1e0-…`; `BlocBuy Buyer Catalog.dc.html`, `blocbuy-theme.css`, `fig-tokens.css`.
- Verified: `pnpm check` exit 0 (vitest 6 files / 18 tests, knip 0); build ok; e2e 1 passed / 2 skipped; MSW screenshots at 390+1280 (populated, page 2, category, empty), 0 errors. Follow-up: `msw=1` exact match, past-last-page redirect, route test (note 40; vitest 7 files / 21 tests). Second follow-up: tile/start-link `relative` so stretched link no longer covers page; card test added.
- Not verified: the real backend, loading and error states in a browser, Inter offline, Safari 16.4.
- Problems: CR-11 no deal sizes on the card DTO (progress cue omitted); CR-15 `--muted` 4.41:1 (used as designed); CR-10 category strings unknown; A2's CSP must allow Google Fonts. Open.

## 2026-09-18 - @limjiechao - T2-02 product detail with deal progress and not-found state (#44, #93)

- Kind: feature (PR-G, UC1 detail); artboards over plan (note 41); no ADR, no NFR.
- Changed: `DealProgress` and `deal-time` with tests, the `/products/$productId` route (loader, gallery, deal progress, skeleton, error, `notFoundComponent`), MSW detail seed with a 404; plan steps 1–4, notes 41–42; T2-01 follow-up included.
- Sources: `design-screens.md` §4.2/§4.5; `BlocBuy Buyer Listing Detail.dc.html` and `BlocBuy Buyer Errors.dc.html`; HeroUI props from the installed 3.2.5 types (offline-first after the first attempt stalled on the network).
- Verified: `pnpm check` exit 0 (vitest 9 files / 33 tests, knip 0); build ok; e2e 1 passed / 2 skipped; MSW screenshots (open, urgent, no-bloc, not-found) at 390+1280, 0 errors. Review follow-ups: loader route test (HTTP 404 + `_404_` envelope → not-found card), ended-header clock fix, broken gallery image dropped (vitest 10 files / 40 tests); round 2 (note 43): `parseDeadline` guards bad `expireTime`, data-less 200 rejected, unwired buttons `isDisabled`. Round 3 declined: loader `staleTime: "static"` is rule 7, detail keeps refetching seats.
- Not verified: the real backend and its not-found shape (note 42), loading and error states in a browser, the sticky bar while scrolling.
- Decision: not-found on `status === 404` or a `_404_` code (note 42); breadcrumbs as plain router links; Join / Start a bloc / Buy alone rendered unwired until T2-04 and UC3/UC4.
- Problems: CR no 404 declared for `getListing`; CR `ActiveDealResponse` has no starter, number or per-bloc list (one "Open bloc" row renders); CR no listing status for "unavailable". Open.

## 2026-09-18 - @limjiechao - T2-03 catalog filter: category chips and keyword search (#44, #47, #93 PR-H)

- Kind: feature (PR-H, UC1 filter); artboards over plan (note 44); TanStack Form + valibot as planned; no ADR, no NFR.
- Changed: `FilterForm`, `categories`, `filter-form.schema` with tests; `keyword` in the search schema, the `/` query and the MSW seed; chip row and search in the artboard slot; knip ignores for form and user-event removed (note 25 closed); plan steps 1–5 with a dated step 4 correction (`keyword` now, contract live since PR #107), note 44.
- Sources: `design-screens.md` §4.1 "Filter" and §7 Q3/Q4; `BlocBuy Buyer Catalog.dc.html` chip markup; the HeroUI Kit sheet's parked `SearchField`; installed 3.2.5 types, offline-first.
- Verified: `pnpm check` exit 0 (vitest 10 files / 43 tests, knip 0); build ok; e2e 1 passed / 2 skipped; MSW screenshots (Household, `keyword=rice`, empty, typed submit) at 390+1280, 0 errors.
- Not verified: the real backend's category strings and keyword matching; a screen-reader pass; Inter offline.
- Decision: chips as router links with `aria-current` on the selected one; keyword submits on Enter only, empty or unchanged submit is a no-op, URL keyword cut to 64.
- Problems: CR no search field in the artboards (placement and empty copy are ours); CR-10 category strings unpublished; the contract's `ai` flag has no design. Open.
- PR #119 review (r4045128139/44/58/76): restack had dropped the plan record — re-added as note 44. Household chip sends seed's `home`; empty-state links keep `size`; one `keyword-match.ts` shared by seed and route test. Three outdated #115 threads answered as landed (9b67149).

## 2026-09-18 - @limjiechao - T2-04 session hook, /login, /auth/callback, _authenticated guard, /account, gated sheet (#42, #93)

- Kind: feature (UC2 sign-in, frontend half); the guard and the gated sheet are UX only, the backend enforces (ADR 0002); NFR-01 unchanged; artboards over plan (note 45); no ADR.
- Changed: `useSession` and helpers, `GoogleSignInButton`, `GatedActionSheet`; routes `/login`, `/auth/callback`, `_authenticated`, `/account`; root `beforeLoad` session; gated detail-page actions; neutral header Sign in with avatar + name when signed in; shared `render-route.tsx`; `me` in the MSW seed (`?msw=1&me=1` signs in); plan steps 1–8, notes 45–46.
- Sources: `design-screens.md` §3, §4.3, §4.4, §7 Q1/Q13–Q17; `BlocBuy Buyer Sign In.dc.html`, `BlocBuy Buyer My Blocs.dc.html`; contract auth operations; installed 3.2.5 types, offline-first.
- Verified: session suites 4 files / 20 tests (red first); `pnpm check` exit 0 (vitest 18 files / 74 tests, knip 0); build ok; e2e 1 passed / 2 skipped after fixing blank app on preview `me` ECONNREFUSED (note 46); 13 MSW screenshots at 390+1280, 0 errors.
- Not verified: the real Google round trip and the `blocbuy-token` cookie (T2-06 after B5); `Modal placement="auto"` breakpoint; `Avatar.Image` with a real URL.
- Decision: return path in sessionStorage (CR-13); root `beforeLoad` falls back to anonymous on non-401 failures while the callback stays strict (note 46); header Sign in goes to `/login`.
- Problems: CR-13 no return URL through Google; CR-16 Google mark is a placeholder; Q16 no artboard for the signed-in header or logout; Q15 `/account` interim. Open.
- Restack after #115 squash (2026-09-19, r4045102864): rebase had dropped gated detail actions — re-added over dev's disabled buttons; `pnpm check` 87 tests, build ok.
- PR #120 review (r4045129508, r4050400116/23): return paths reject backslash (`%5C` parses as `/`), with regression tests; open-bloc Join opens the sheet via `onGated`; seed reads `me=1` once at worker start. Plan record re-added as notes 45–46.

## 2026-09-18 - @limjiechao - T2-05 logout mutation clears the session (#42, #93 PR-I)

- Kind: feature (session; UX only, the backend invalidates the cookie); no ADR, no NFR.
- Changed: `use-logout.ts` (generated `logoutMutation`, `unwrapResult`, `me` settled on the anonymous envelope, navigate home; error keeps the session); header avatar links to `/account` and a ghost "Sign out" button with pending state and inline alert; `logout` in the MSW seed; plan steps 1–3, note 47.
- Verified: red first (2 failed) then 5 passed; `pnpm check` exit 0 (vitest 18 files / 76 tests, knip 0); build ok; e2e 1 passed / 2 skipped; MSW screenshots (signed-in header, after sign-out) at 390+1280, 0 errors; HeroUI props from installed types, offline-first.
- Not verified: the real backend clearing `blocbuy-token` (T2-06 after B5); the error branch only under MSW.
- Decision: `setQueryData` on the anonymous envelope instead of `removeQueries` (no refetch, no 401 round trip); a neutral button beside the avatar rather than an avatar menu (note 47).
- Problems: Q16 no artboard for the signed-in header or a logout control (design owner picks button vs menu and the string); Q15 avatar links to the interim `/account`. Open.
- PR #121 review (r4050777123/27/30): all three threads were plan text settled below this branch. Plan record re-added as note 47 (restack had dropped it). Round 2 (r4053031037): step 2 names the shipped "Sign out" / "Sign in" controls.

## 2026-09-18 - @limjiechao - T2-06 login e2e journey, signed-in half gated on a hand-seeded storage state (#42, #98, #93 PR-I)

- Kind: feature and test (Playwright journey per ADR 0011); no NFR row covers e2e or login; the guard under test is UX only. No ADR.
- Changed: `e2e/login.spec.ts` (an "anonymous visitor" describe on every target; a "signed-in buyer @auth" describe gated on `e2e/.auth/dev.json` and `E2E_TARGET=dev`); `e2e/README.md` (one-time codegen seeding after the B5 #98 console check, re-seed after a sign-out run); `e2e/.auth/.gitkeep`; plan step 2 ticked, steps 1 and 3 annotated as the user's, note 48.
- Verified: build ok; e2e 3 passed, 5 skipped (`/account` redirect + Google-button return-path passed; gated sheet and both `@auth` skipped; two dev-only smokes); `pnpm check` exit 0 (vitest 18 files / 76 tests, knip 0). Offline-first.
- Not verified: the signed-in half (needs the user's `e2e/.auth/dev.json` from a real Google login after B5, then `E2E_TARGET=dev E2E_BASE_URL=… pnpm e2e --grep @auth`); the gated-sheet case against the real backend; the `blocbuy-token` cookie name after sign-out; any dev run before T1-14.
- Decision: one journey file instead of the plan's `account.spec.ts`; the login navigation is answered with a 204 so the test never touches Google (note 48).
- PR #122 review (r4046597269, r4052574005): gated-sheet journey stubs via `page.route` — runs on preview and dev alike (4 passed / 4 skipped locally, no more gated-sheet skip); sign-out test requires `blocbuy-token` expired before asserting jar empty. Plan record re-added as note 48. Round 2 (r4053028508): note 48 describes the stubbed journey.
- Problems: step 1 is a human action and B5's console check is unconfirmed (#98); asked in the PR body; the `@auth` tests report the codegen command as the skip reason. Open, user-owned.

## 2026-09-20 - Yorick - Stripe Checkout, browser and Sandbox acceptance

- Kind: feature, security, data consistency; NFR-21 and NFR-22; tracking: PR #126.
- Changed: server-owned payment intents, idempotent Hosted Checkout, signed webhooks, buyer-owned receipts, product-page START/JOIN, refund safeguards and generated-client integration.
- Verified: isolated PostgreSQL suites and frontend gates passed; five real Sandbox payments covered backend/browser START/JOIN and recovery, and one SGD 10 refund remained unique on recheck. Evidence: `backend/docs/payment/stripe-sandbox-acceptance-20260920.md`.
- Decisions/problems: return URLs never assert payment state; refunds remain separate from successful payments. Google OAuth, shared deployment, production collaborators and live mode were not verified.

## 2026-09-20 - Yorick - Compatibility, default-off rollout and local-interface removal

- Kind: maintainability, security, availability; NFR-23; tracking: PR #126.
- Changed: separated Checkout from original payment contracts, gated it with `stripe-sprint2`, and removed local HTTP/operator/runtime interfaces. Test collaborators and SQL remain test-only.
- Verified: source/contract guard preserved 192 files, 13 paths and 23 schemas; post-removal backend, isolated PostgreSQL, frontend, build and JAR-inspection checks passed.
- Decisions/problems: one backend capability controls frontend availability. Production intent/order bindings, refund-worker wiring and shared deployment remain open. Current checklist: `backend/docs/payment/stripe-release-cleanup-20260920.md`.

## 2026-09-20 - Yorick - PR #126 SonarCloud and receipt isolation fixes

- Kind: maintainability, security; NFR-06 and NFR-21; tracking: PR #126.
- Changed: enabled isolated PostgreSQL CI coverage, simplified settlement/recovery, added failure-path tests and scoped receipt caching by user.
- Verified: 725 backend and 127 frontend tests passed; aggregate JaCoCo covered every line of `StripeCheckoutService` and `RefundService`. Evidence: `backend/docs/payment/stripe-sonar-fixes-20260920.md`.
- Problems: remote SonarCloud remains authoritative; no gate was weakened and deployment is separate.

## 2026-09-21 - @PPsyrius - Documentation cleanup: stale-file pass, CI/prod consolidation (#93)

- Kind: maintainability (docs); no code, no NFR change. Frontend docs (`frontend/README.md`, `frontend/docs/plan.md`, `blueprint.md`, `blueprint-review.md`, `design.md`, `design-screens.md`, `plan-session-prompt.md`) are out of scope here; they move with `frontend_doc_cleanup`.
- Changed: rewrote repository structure against the verified tree; removed superseded §7.3/prod TODO material and consolidated pipeline/prod docs; marked bootstrap documentation complete; condensed handover 343 → 81 lines. Review fixes covered the `sonar-backend` dependency, `ORIGIN_SECRET` exception, infra/backend secret boundary, removal of apply/user-data claims, fenced inventories, and an SSM cloud-init poll requiring `Success`.
- Format: all touched docs reflowed to one line per item (tables/fences/ASCII blocks preserved; one table-in-quote caught and restored).
- Not changed: skeleton (locked D-table + §13 anchors); runbook/operator-guide/`backend-pipeline.md` canonical; badges live status.
- Verified: `git diff --check` clean; zero refs to deleted TODO; appendix anchor present; fence counts even; no stray `sonar` ID; extracted SSM snippet passes `bash -n`. Skipped: LanguageTool list-voice nit (intentional).
- Not verified: GitHub rendered Markdown — check on the PR page.

## 2026-09-21 - l0sgAi (Liu Jin) - PR #126 review fixes: migration integration and convention alignment

- Kind: correctness, maintainability; NFR-24 (new); tracking: PR #126 review by order side.
- Changed: moved Stripe DDL into Flyway V9-V11, removed duplicate indexes/backfill, aligned lifecycle statuses, shared the Stripe client, and removed the duplicate order-history API in favour of `/orders/me`.
- Decisions: keep `intent_id` distinct from the provider ID and keep result/audit codes uppercase while lifecycle statuses remain lowercase.
- Verified: JDK 21 `./mvnw -B verify` migrated PostgreSQL 18 through V11; touched payment classes had full line/branch coverage and the regenerated contract had no backward-incompatible change.
- Problems: order-branch migration numbering still needs reconciliation; see the release checklist.

## 2026-09-21 - l0sgAi (Liu Jin) - Sync `dev` into the stripe branch and retire the orphaned order history

- Kind: merge and frontend integration; NFR-23; tracking: PR #126.
- Changed: merged `origin/dev`, retained NFR-19/20 and renumbered Stripe rows to NFR-21-24. Removed the orphaned `/account` order-history consumer until `/orders/me` enters the contract.
- Verified: generated client, 118 frontend tests, knip and production build passed.
- Problems: the merge hook exposed the stale order-history call; the following commit removed it.

## 2026-09-21 to 2026-09-22 - @PPsyrius - PR #126 quality, coverage and evidence review

- Kind: maintainability, performance, test and process evidence; NFR-06 and NFR-20-25; tracking: PR #126.
- Changed: replaced repeated refund-state literals; extracted receipt title/details/refund rendering to reduce complexity; bounded receipt polling by error state and a fixed 60-second deadline; expanded payment coverage; enforced the soft-delete boundary across payment and refund worker reads/writes; corrected the exact OAuth callback guidance; applied readonly React props and static Mockito imports; consolidated 17 Stripe Markdown changes into five canonical records and removed 12 superseded reports/drafts.
- Verified: JDK 21 `./mvnw.cmd -B -ntp verify` passed, including 32 Testcontainers integration tests and Flyway V1-V11 on PostgreSQL 18; aggregate JaCoCo had no missed lines or partial branches across 32 touched payment files. The Node 24 `pnpm check` gate passed TypeScript, Oxlint, Oxfmt, 151 tests and Knip; touched payment/product routes had 100% line and branch coverage.
- Decision: prefer behavior-preserving helpers and direct branch tests over suppressions or duplicate assertions; treat soft-deleted payments as absent from operational payment/refund flows; keep the sprint log, NFR register, release boundary, Sandbox acceptance and quality report as the canonical evidence set.
- Problems: remote SonarCloud and shared deployment remain authoritative and were not run locally.
## 2026-09-22 - @PPsyrius - Keep automated updates on Java 21 and Boot 4

- Kind: nfr, decision, chore
- Changed: `.github/dependabot.yml` ignores major `eclipse-temurin` Docker updates plus majors for the Boot-4-line Maven artifacts (`org.mybatis.spring.boot*`, `cn.dev33*`, `org.springdoc*`, `org.apache.tomcat*`); same-line maintenance still flows. The ignores cover non-security version updates only; security updates remain eligible. CI policy and NFR-26 record the agreed Java 21 LTS + Boot 4 baseline.
- Verified: `pnpm dlx yaml@2.9.1 valid .github/dependabot.yml` (before and after the Maven rules); `git diff --check` clean. Maven tests not run because no application or build source changed. Dependabot's next update run is not locally reproducible.
- Decisions: `docs/ci/devsecops-pipeline-plan.md` "Java runtime update policy"; later Java releases require a coordinated stack decision rather than an automated dependency PR. Scope deliberately Java/Boot only: ArchUnit, Stripe, Enforcer plugin, Flyway, npm and Actions reviewed and left unpinned (test-only, unrelated stacks, or fail-closed).
- Problems: register row takes NFR-26 since Stripe claimed NFR-21–25 on `dev`; commit and push are user-owned.
