# BlocBuy Frontend Implementation Plan

**Goal (Sprint 2, completed):** Deliver the buyer walking skeleton (catalog, detail, `/account` guard, logout, filters) to dev CloudFront with CI, Playwright and an evidence trail. Tier 3 follow-ups are tracked below; the deferred admin console is in [`admin-plan.md`](admin-plan.md).

**Architecture:** Pure client-side React SPA built into static files, served from S3 through the originally hand-built, now Terraform-adopted dev CloudFront distribution. The backend is on the same origin at `/api/*` (cookie session, no CORS). TanStack Router owns URL state and auth guards; TanStack Query uses the client generated from `contracts/openapi.json`, with one shared `Result<T>` unwrap boundary.

**Tech Stack:** React 19 CSR, TypeScript 7, Vite 8, TanStack Router/Query/Form, HeroUI 3, Tailwind 4, generated hey-api client and Node 24/pnpm 12. `frontend/package.json` owns current exact versions; §1 identifies the planning baseline.

**Planning sources:** [`blueprint.md`](blueprint.md) and [`blueprint-review.md`](blueprint-review.md) capture the 2026-09-15 baseline. Later ADRs, shipped code and runbooks supersede their historical OPEN items; §12 indexes old note references.

**Planning date:** 2026-09-16. **Updated:** 2026-09-29. Sprint 2: 2026-09-14 to 2026-09-25. Tier 2 source work and T2-11's live policy recheck are complete; Tier 3 rows record delivered follow-ups. New buyer tasks live in [`backlog.md`](backlog.md).

## Global Constraints

- React 19.3, pure CSR, no SSR framework. Static files to the existing S3 + CloudFront (`https://d1b3np88z657cp.cloudfront.net`).
- TanStack Router with `@tanstack/router-plugin` file-based routes; plugin listed before `@vitejs/plugin-react`.
- React Compiler ON; convention: no manual `useMemo` / `useCallback` / `React.memo`.
- TypeScript 7.0.x (`typescript` package, `tsc` entry point). No programmatic compiler API until 7.1.
- Tailwind CSS 4.3; browser floor Safari 16.4+, Chrome 111+, Firefox 128+ (NFR, ADR 0001).
- HeroUI 3.2.x over raw React Aria Components. Exact version pins everywhere, no caret ranges.
- pnpm 12 via `packageManager` + Corepack; Node 24 LTS via `.mise.toml` and `.nvmrc`.
- TanStack Query 5 for server state. hey-api with `valibot`, `@tanstack/react-query`, and `msw` plugins (implemented as exact `next` snapshot `0.0.0-next-20260824173136`; the planning brief named `0.99.x`, which needs the classic TypeScript compiler API removed in TS 7 — see §12 anchor 19). `baseUrl` overridden to `/`.
- `Result<T>` envelope types kept as generated; `data` unwrapped in one shared `select`; throw on non-2xx or non-OK `code`. No generator transform.
- Generated client is NOT committed. `pnpm gen:api` runs in `prepare`, first in `pnpm check`, first in `pnpm build`. Output dir gitignored and excluded from oxlint, oxfmt, knip.
- valibot only at `validateSearch` and forms. Never hand-write API response schemas.
- TanStack Form 1.x. MSW 2 handlers generated, loaded in Vitest `setupFiles` and behind a dev flag.
- Same origin: SPA at `/`, backend at `/api/*`. Backend has no context path; `/api/v1` comes from controller mappings. CloudFront does not rewrite paths.
- Auth: Google OAuth only. Login = full-page navigation to `GET /api/v1/auth/oauth2/google`. Probe `GET /api/v1/auth/me` → `{userId, nickname, avatarUrl}` (B4 landed in PR #89; it was `{loginId}` at planning time). Logout `POST /api/v1/auth/logout`. Unauthenticated → JSON 401 envelope. `GET /api/v1/products/**` anonymous.
- Auth state: `useSession` query; the 401 interceptor settles `me` to an anonymous success envelope and never redirects. Route `beforeLoad` guards own redirects.
- Local dev: `docker compose up` from `backend/` → backend on `http://localhost:8589`; Vite proxy `/api` with prefix preserved. For a shared-origin stack, see [`fullstack-localhost/README.md`](../../fullstack-localhost/README.md).
- No `VITE_*` variables, no `import.meta.env`. One artifact promoted dev → prod. Future runtime config only via `/config.json` with `no-cache`.
- Dev CloudFront was hand-built, then adopted by `infra/terraform/frontend-stack/` in PR #190; changes follow its reviewed Terraform plan and the runbook. SPA fallback uses a CloudFront Function on viewer-request of the default behavior. No distribution-wide custom error responses.
- Cache headers: hashed assets `public,max-age=31536000,immutable`; `index.html` `no-cache`; invalidate `/index.html` on deploy.
- `deploy-s3-dev` on push to `dev`, gated by the `frontend-dev` environment and `ENABLE_S3_DEPLOY`, OIDC role from the runbook.
- oxlint plugins `typescript`, `react`, `react-perf`, `jsx-a11y`, `import`, `unicorn`, `vitest`, `promise`; categories `correctness`, `suspicious`, `perf`; `--type-aware`. No `eslint-plugin-react-hooks`. TanStack query/router rules are hosted by oxlint JS plugins (spike won, T1-09); no project ESLint configuration — the minimal-ESLint fallback in ADR 0006 is the escape hatch only.
- One gate: `pnpm check` = `gen:api` → `tsc --noEmit` → `oxlint --type-aware` → `oxfmt --check` → `vitest run` → `knip`.
- CI `ci-frontend.yml` mirrors `ci-backend.yml`; contract breaking-change detection stays in `ci-backend.yml`.
- Dependabot (not Renovate): `npm` ecosystem for `/frontend`, groups `tanstack` and `heroui`, daily.
- `frontend/AGENTS.md` conventions + Intent guidance block; `frontend/CLAUDE.md` = `@AGENTS.md`. Root `.claude/settings.json` hooks scoped to `frontend/**`. Do NOT run `intent hooks install`.
- ADRs in `frontend/docs/adr/`, Nygard format, from 0001, README index. Every PR: issue link, ADR names, `pnpm check` result or CI link, screenshot or Playwright trace for UI changes (blueprint-review D9).

---

## 1. Stack sources and planning baseline (2026-09-16)

Use `frontend/package.json` and `pnpm-lock.yaml` for current exact versions and `.github/workflows/ci-frontend.yml` for pinned Actions. The original baseline was rechecked against npm on 2026-09-16 and remains available in the git history and [`blueprint-review.md`](blueprint-review.md) §1.4. ADRs 0001, 0003, 0010 and 0013 explain the settled stack and the prerelease hey-api pin.

## 2. PR map and evidence rule

Every PR targets `dev`, is titled as a Conventional Commit with scope, links its issue, names the ADR(s) it implements, pastes the `pnpm check` result or CI link, and attaches a screenshot or Playwright trace for UI changes (D9). The `.github/PULL_REQUEST_TEMPLATE.md` from T1-12 carries the checklist.

| PR | Scope | Tasks | ADRs cited | Reviewer routing |
|---|---|---|---|---|
| PR-A `docs(frontend): plan, ADR stubs, sprint 2 log` | this session's output + ADR bodies | T0-00, T1-15 | 0001–0013 | @limjiechao |
| PR-B `docs(deploy): frontend runbook steps and IAM examples` | runbook §14–§17, `deployment/frontend/` | T1-03 | 0002, 0004, 0005, 0007 | @l0sgAi (runbook), @PPsyrius (`deployment/`) |
| PR-C `feat(frontend): scaffold, generated client, quality gate` | toolchain + scaffold + client + `pnpm check` | T1-04..T1-09, T1-11 | 0001, 0003, 0006, 0009, 0010, 0011, 0013 | @limjiechao; `.gitignore` line → @PPsyrius |
| PR-D `chore(agents): frontend AGENTS.md, Claude hooks, Intent` | agent config | T1-10 | 0012 | @limjiechao |
| PR-E `ci(frontend): ci-frontend workflow, security paths, dependabot npm, PR template` | `.github/**` | T1-12 | 0004, 0008, 0011 | @PPsyrius (required) |
| PR-F `test(frontend): Playwright smoke against dev CloudFront` | e2e | T1-13, T1-14 | 0005, 0011 | @limjiechao |
| PR-G `feat(frontend): products list and detail` | #44 | T2-01, T2-02 | 0001, 0003, 0009 | @limjiechao |
| PR-H `feat(frontend): listing filter form synced to URL` | #44 / #47 | T2-03 | 0001 | @limjiechao |
| PR-I `feat(frontend): session, header login/logout, /account guard` | #42 | T2-04, T2-05, T2-06 | 0002 | @limjiechao |
| PR-J `feat(infra): import dev frontend edge into frontend-stack` | Terraform | T2-07 | 0007 | @PPsyrius |
| PR-K `docs(frontend): native compiler spike and react-table evaluation` | ADR addenda | T2-08, T2-09 | 0010, 0001 | @limjiechao |
| PR-L `chore(skills): adr, api-client-regen, agile-evidence-capture` | root `.claude/skills/` | T2-10 | 0008, 0012 | @limjiechao |
| PR-M `chore(frontend): tighten CSP after headers policy lands` | runbook JSON, sprint log | T2-11 | 0002 | @limjiechao, @PPsyrius |

## 3. Task table

Columns: id, tier, purpose, files touched, exact verification command, depends-on (task or blocker ids), GitHub issue advanced. Doc URLs cited per row are the pages verified on 2026-09-16. `FE` = `frontend/`. Commands run from the repository root unless prefixed `cd frontend &&`. Step-level detail for each task is in §4.

### Tier 1 (week ending 2026-09-20): toolchain, scaffold, agent config, CI, runbook, deploy, smoke

| id | tier | purpose | files touched | verification command | depends-on | issue |
|---|---|---|---|---|---|---|
| T0-00 | 1 | Commit this plan and the ADR stubs (this session). | `FE/docs/plan.md`, `FE/docs/adr/README.md`, `FE/docs/adr/0001..0013-*.md` | `git log --oneline -1 -- frontend/docs/plan.md` | — | new: Sprint 2 issue (T1) |
| T1-01 | 1 | Probe dev CloudFront on day 1: `/api` behavior, SPA fallback state, cookie flags. Record in the sprint log. Zero code. | `docs/agile/sprint-02/log.md` | `curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' https://d1b3np88z657cp.cloudfront.net/api/actuator/health; curl -sS -o /dev/null -w '%{http_code}\n' https://d1b3np88z657cp.cloudfront.net/products/probe; curl -sSI https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google \| grep -i 'set-cookie\|location'` | — | #44, A1, B3 |
| T1-02 | 1 | File blocker issues B1–B5, A1–A4, T1–T4 from §10, linked to the Sprint 2 issue. | GitHub only | `gh issue list --limit 100 --json title --jq '[.[] \| select(.title \| test("^\\[(B[1-5]\|A[1-4]\|T[1-4])\\]"))] \| length'` = 13 | T0-00 | new (13 issues) |
| T1-03 | 1 | Runbook PR: §14 SPA fallback (promote §7.3 to mandatory), §15 response-headers policy, §16 OIDC deploy role, §17 `frontend-dev` environment and variables; §10 npm → pnpm; policy examples. Content in §8. | `docs/deploy/dev-cloudfront-deployment.md`, `deployment/frontend/iam/README.md`, `deployment/frontend/iam/github-frontend-deploy-trust-policy.json.example`, `deployment/frontend/iam/github-frontend-deploy-policy.json.example`, `deployment/frontend/cloudfront/spa-fallback.js`, `deployment/frontend/cloudfront/response-headers-policy.json` | `for f in deployment/frontend/iam/*.example deployment/frontend/cloudfront/*.json; do python3 -m json.tool "$f" >/dev/null && echo "ok $f"; done; node --check deployment/frontend/cloudfront/spa-fallback.js` | T1-01 | #48, A1, A2, A3, A4 |
| T1-04 | 1 | Runtime and package-manager pins: `.mise.toml`, `.nvmrc`, `package.json` with `packageManager` and `engines`, `.npmrc`; root `.gitignore` entries. | `FE/.mise.toml`, `FE/.nvmrc`, `FE/package.json`, `FE/.npmrc`, `.gitignore` | `cd frontend && mise install && mise exec -- node --version \| grep -E '^v24\.' && corepack pnpm --version \| grep -x 12.4.2` | — | #44 |
| T1-05 | 1 | Vite + React + TS + Tailwind + HeroUI scaffold with file-based routes; root route, index route, not-found. Docs: https://tanstack.com/router/latest/docs/installation/with-vite , https://tanstack.com/router/latest/docs/api/file-based-routing , https://heroui.com/docs/react/getting-started/quick-start , https://vite.dev/config/server-options , https://vite.dev/config/build-options , https://github.com/vitejs/vite-plugin-react/tree/main/packages/plugin-react | `FE/vite.config.ts`, `FE/tsconfig.json`, `FE/index.html`, `FE/src/main.tsx`, `FE/src/router.tsx`, `FE/src/styles/app.css`, `FE/src/routes/__root.tsx`, `FE/src/routes/index.tsx`, `FE/src/routeTree.gen.ts` (generated, committed, read-only) | `cd frontend && pnpm install --frozen-lockfile && pnpm build && test -f dist/index.html && ls dist/assets/*.js` | T1-04 | #44 |
| T1-06 | 1 | hey-api client generation with valibot, TanStack Query, MSW plugins; runtime config `baseUrl: '/'`; `prepare` hook; gitignore of output. Docs: https://heyapi.dev/openapi-ts/configuration , https://heyapi.dev/openapi-ts/clients/fetch , https://heyapi.dev/openapi-ts/plugins/valibot , https://heyapi.dev/openapi-ts/plugins/tanstack-query , https://heyapi.dev/openapi-ts/plugins/msw | `FE/openapi-ts.config.ts`, `FE/src/api/hey-api.ts`, `FE/package.json` (`gen:api`, `prepare`), `.gitignore` | `cd frontend && pnpm gen:api && test -f src/api/generated/@tanstack/react-query.gen.ts && test -f src/api/generated/valibot.gen.ts && test -f src/api/generated/msw.gen.ts && git check-ignore -q src/api/generated` | T1-05 | #44 |
| T1-07 | 1 | `Result<T>` unwrap `select` helper, `ApiError`, 401 interceptor that settles `me` to an anonymous success envelope without redirecting, `QueryClient` defaults; unit tests. Docs: https://heyapi.dev/openapi-ts/clients/fetch (interceptors), https://tanstack.com/query/latest/docs/framework/react/guides/query-options | `FE/src/api/envelope.ts`, `FE/src/api/envelope.test.ts`, `FE/src/api/client.ts`, `FE/src/api/query-client.ts` | `cd frontend && pnpm vitest run src/api` | T1-06, T1-08 | #44, #42 |
| T1-08 | 1 | Quality toolchain and the `pnpm check` gate: oxlint (+type-aware), oxfmt, knip, Vitest + Testing Library + happy-dom + generated MSW handlers, Lefthook. Docs: https://oxc.rs/docs/guide/usage/linter/config-file-reference.html , https://oxc.rs/docs/guide/usage/linter/type-aware , https://oxc.rs/docs/guide/usage/formatter/config.html , https://knip.dev/reference/configuration , https://lefthook.dev/configuration/root/ | `FE/.oxlintrc.json`, `FE/.oxfmtrc.json`, `FE/knip.json`, `FE/vitest.config.ts`, `FE/src/test/setup.ts`, `FE/src/test/msw.ts`, `FE/package.json` (scripts), `lefthook.yml` (root) | `cd frontend && pnpm check` exits 0 | T1-06 | #44 |
| T1-09 | 1 | Timeboxed spike (2 h): load `@tanstack/eslint-plugin-query` and `@tanstack/eslint-plugin-router` through oxlint `jsPlugins`; on failure install the minimal ESLint fallback. Record outcome in ADR 0006. Docs: https://oxc.rs/docs/guide/usage/linter/js-plugins | `FE/.oxlintrc.json` or `FE/eslint.config.js`, `FE/package.json` (`lint`), `FE/docs/adr/0006-*.md` | `cd frontend && printf 'import { useQuery } from "@tanstack/react-query"\nexport const x = () => useQuery({ queryFn: () => 1 })\n' > src/lint-fixture.ts; pnpm lint; rc=$?; rm src/lint-fixture.ts; test $rc -ne 0` (fixture must FAIL on a `@tanstack/query/*` rule) | T1-08 | #44 |
| T1-10 | 1 | Agent config: `FE/AGENTS.md`, `FE/CLAUDE.md`, root `.claude/settings.json` + three hook scripts, Intent install with `@tanstack/*` scope and `--map`, verify transitive `router-core` discovery under pnpm. Docs: https://code.claude.com/docs/en/hooks , https://code.claude.com/docs/en/hooks-guide , https://code.claude.com/docs/en/permissions , https://code.claude.com/docs/en/memory , https://tanstack.com/intent/latest/docs/cli/intent-install , https://tanstack.com/intent/latest/docs/concepts/configuration | `FE/AGENTS.md`, `FE/CLAUDE.md`, `.claude/settings.json`, `.claude/hooks/frontend-fix.sh`, `.claude/hooks/frontend-typecheck.sh`, `.claude/hooks/frontend-bootstrap.sh`, `FE/package.json` (`intent.skills`), `FE/docs/tanstack-skills.json` | `cd frontend && pnpm dlx @tanstack/intent@0.4.0 list --json \| jq -e '[.[] \| select(.packageName=="@tanstack/router-core")] \| length >= 9' && grep -q intent-skills AGENTS.md && bash -n ../.claude/hooks/*.sh` (excludes `ssr`) | T1-05, T1-08 | #44 |
| T1-11 | 1 | First component test proving the loop: `Header` renders, MSW handler from `msw.gen.ts` answers `me` with 401, Testing Library query passes. | `FE/src/components/Header.tsx`, `FE/src/components/Header.test.tsx` | `cd frontend && pnpm vitest run src/components/Header.test.tsx` | T1-07, T1-08 | #42 |
| T1-12 | 1 | `.github` PR (needs @PPsyrius): `ci-frontend.yml` (jobs in §7.1), `security.yml` path filters + pnpm dependency scan, `dependabot.yml` npm entry, CODEOWNERS additions, `.gitignore`, `PULL_REQUEST_TEMPLATE.md`, CONTRIBUTING required-check line. Docs: https://github.com/pnpm/action-setup , https://github.com/actions/setup-node , https://github.com/aws-actions/configure-aws-credentials , https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference , https://playwright.dev/docs/ci-intro | `.github/workflows/ci-frontend.yml`, `.github/workflows/security.yml`, `.github/dependabot.yml`, `.github/CODEOWNERS`, `.github/PULL_REQUEST_TEMPLATE.md`, `.gitignore`, `CONTRIBUTING.md` | `docker run --rm -v "$PWD:/repo:ro" -w /repo rhysd/actionlint:latest -color` then `gh pr checks <PR-E number> --watch` shows `frontend-check` green | T1-08, T1-13, T3 | #44, #48, T3, T4 |
| T1-13 | 1 | Playwright smoke: home renders; with `E2E_TARGET=dev` also deep link returns the SPA (A1 evidence) and `/api/actuator/health` is UP through CloudFront. Trace on first retry. Docs: https://playwright.dev/docs/ci-intro , https://playwright.dev/docs/browsers | `FE/playwright.config.ts`, `FE/e2e/smoke.spec.ts`, `FE/package.json` (`e2e`), `.gitignore` | `cd frontend && pnpm exec playwright install --with-deps chromium && pnpm e2e` (local preview), then `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e` | T1-05 | #44, A1 |
| T1-14 | 1 | First deploy of `dist/` to the dev bucket. Preferred: `deploy-s3-dev` job after A3+A4. Fallback: account holder runs runbook §10 by hand. | none (S3 objects, CloudFront invalidation) | `curl -sS https://d1b3np88z657cp.cloudfront.net/ \| grep -o '/assets/index-[A-Za-z0-9_-]*\.js' && curl -sSI https://d1b3np88z657cp.cloudfront.net/index.html \| grep -i 'cache-control: no-cache'` | T1-05, T1-12, A3, A4 (fallback: any AWS operator) | #44, A3, A4 |
| T1-15 | 1 | Write ADR bodies 0001–0013 from the stubs; start `docs/agile/sprint-02/log.md` with T1-01 results and the blocker table. | `FE/docs/adr/*.md`, `FE/docs/adr/README.md`, `docs/agile/sprint-02/log.md` | `grep -L '^## Decision' frontend/docs/adr/0*.md \| wc -l` prints 0; `grep -l 'Status: Accepted' frontend/docs/adr/0*.md \| wc -l` ≥ 8 | T0-00, T1-09 (0006 outcome) | Sprint 2 issue (T1) |

### Tier 2 (week ending 2026-09-25): features, Terraform import, spikes

| id | tier | purpose | files touched | verification command | depends-on | issue |
|---|---|---|---|---|---|---|
| T2-01 | 2 | Products list at `/`: `validateSearch` (page ≥ 1, size 1–100 default 20, category optional) with valibot; route `loader` warms `pageListingsOptions` via `queryClient.query`; HeroUI card grid with dual price; pagination via `Link search`. Docs: https://tanstack.com/router/latest/docs/framework/react/guide/search-params , https://tanstack.com/router/latest/docs/framework/react/guide/external-data-loading , https://tanstack.com/query/latest/docs/framework/react/guides/prefetching | `FE/src/routes/index.tsx`, `FE/src/features/catalog/products-search.ts`, `FE/src/features/catalog/ProductCard.tsx`, `FE/src/features/catalog/ProductCard.test.tsx`, `FE/src/features/catalog/Price.tsx` | `cd frontend && pnpm check && pnpm dev`, open `http://localhost:5173/?page=1&category=food`, screenshot in PR-G | T1-07, T1-08, T1-11 | #44 |
| T2-02 | 2 | Product detail at `/products/$productId`: `getListingOptions`, deal progress (`currentSize/targetSize`, `expireTime`), image gallery, `notFoundComponent` on 404 envelope. Docs: https://tanstack.com/router/latest/docs/framework/react/guide/not-found-errors | `FE/src/routes/products.$productId.tsx`, `FE/src/features/catalog/DealProgress.tsx`, `FE/src/features/catalog/DealProgress.test.tsx` | `cd frontend && pnpm vitest run src/features/catalog && pnpm check` | T2-01 | #44 |
| T2-03 | 2 | Listing filters: artboard category chips plus TanStack Form/valibot keyword search; both are URL state and reset pagination. `keyword` is live in the contract. Docs: https://tanstack.com/form/latest/docs/framework/react/guides/validation | `FE/src/features/catalog/FilterForm.tsx`, `FE/src/features/catalog/FilterForm.test.tsx`, `FE/src/features/catalog/filter-form.schema.ts`, `FE/src/routes/index.tsx` | `cd frontend && pnpm vitest run src/features/catalog/FilterForm.test.tsx && pnpm check` | T2-01 | #44, #47 |
| T2-04 | 2 | Session and guard: `useSession` over `meOptions`, dedicated `/login`, `sessionStorage` return path, `/auth/callback`, `_authenticated` pathless guard, interim `/account`, gated-action modal, signed-in header. Docs: https://tanstack.com/router/latest/docs/framework/react/guide/authenticated-routes , https://tanstack.com/router/latest/docs/framework/react/guide/router-context | `FE/src/features/session/use-session.ts`, `FE/src/features/session/session.ts`, `FE/src/routes/auth.callback.tsx`, `FE/src/routes/_authenticated.tsx`, `FE/src/routes/_authenticated/account.tsx`, `FE/src/components/Header.tsx`, `FE/src/router.tsx`, `FE/src/routes/__root.tsx`, `FE/src/main.tsx` | `cd frontend && pnpm vitest run src/features/session src/components && pnpm check`; manual: `/account` logged out → `/login?returnTo=%2Faccount`; logged in → shows `nickname` (fallback `userId`) | T1-07, T1-11; B1 | #42 |
| T2-05 | 2 | Logout: `logoutMutation()` validates the envelope, settles `me` to the anonymous envelope, then navigates `/`. | `FE/src/features/session/use-logout.ts`, `FE/src/components/Header.tsx`, `FE/src/components/Header.test.tsx` | `cd frontend && pnpm vitest run src/components/Header.test.tsx` | T2-04 | #42 |
| T2-06 | 2 | Login e2e: anonymous login/guard/gated-action journey always runs; signed-in `@auth` cases use a manually seeded dev `storageState` and skip when it is absent. | `FE/e2e/login.spec.ts`, `FE/e2e/README.md`, `FE/e2e/.auth/.gitkeep`, `.gitignore` | `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @auth` | T2-04; real `@auth` execution also needs B5/dev auth configuration | #42 |
| T2-07 | 2 | Import nine existing dev frontend resources (S3 bucket, policy, public-access block; CloudFront OAC, function, headers policy, distribution; IAM role and inline policy) into separate Terraform state. | `infra/terraform/frontend-stack/*.tf`, `.terraform.lock.hcl`, `infra/terraform/README.md` | Import-only apply: 9 imported, 0 created/changed/destroyed; private post-import `plan -detailed-exitcode` exited 0 (2026-09-25). | T1-03 executed (A1, A2), #48 §3 | #161, PR #190 |
| T2-08 | 2 | Native compiler spike (branch only): `react({ compiler: true })` + `oxc-transform-react`; compare `pnpm check`, `pnpm build` size, Header test; record in ADR 0010 addendum. Not merged as code. | `FE/vite.config.ts` (branch), `FE/docs/adr/0010-*.md` | `cd frontend && pnpm check && pnpm build && du -sh dist/assets` on the spike branch; numbers pasted into ADR 0010 | T1-08 | new: `spike(frontend): native React Compiler via oxc-transform-react` |
| T2-09 | 2 | `@tanstack/react-table` evaluation for #31 (branch only): install 9.2.4, confirm Intent exposes its 6 skills, prototype a 10-row "my orders" table with generated types; recommendation into ADR 0001 addendum. | `FE/package.json` (branch), `FE/src/features/orders/OrdersTable.tsx` (branch), `FE/docs/adr/0001-*.md` | `cd frontend && pnpm dlx @tanstack/intent@0.4.0 list --json \| jq -e '[.[] \| select(.packageName=="@tanstack/react-table")] \| length == 6'` | T1-10 | #31 |
| T2-10 | 2 | Author repo skills `adr`, `api-client-regen`, `agile-evidence-capture` in root `.claude/skills/` after one manual pass of each activity. | `.claude/skills/adr/SKILL.md`, `.claude/skills/api-client-regen/SKILL.md`, `.claude/skills/agile-evidence-capture/SKILL.md` | `for s in adr api-client-regen agile-evidence-capture; do head -5 .claude/skills/$s/SKILL.md \| grep -q '^description:' && echo "ok $s"; done` | T1-15, T1-06, PR-C merged | new: `chore(skills): frontend authored skills` |
| T2-11 | 2 | Tighten the checked-in response-header policy after live/preview CSP probes; keep `'unsafe-inline'` for React Aria styles and recheck live delivery after the operator update. | `deployment/frontend/cloudfront/response-headers-policy.json`, `docs/deploy/dev-cloudfront-deployment.md`, `docs/agile/sprint-02/log/2026-09-23.md` | Preview policy probe passed; live re-apply and dev E2E remain operator steps. | A2, T1-14 | A2 |

### Tier 3 (added 2026-09-23 by #160; delivered follow-ups): payment flow, UC4/UC7/UC8

The payment slice shipped separately in PR #126; see ADR 0014 and §12 note 49. The following rows keep the later implementation and remaining dependencies visible without duplicating the feature code.

| id | tier | purpose | files touched | verification command | depends-on | issue |
|---|---|---|---|---|---|---|
| T3-01 | 3 | UC3: show the first-team quote and countdown before START checkout; START re-prices, so the quote is a preview. | `FE/src/features/payment/{quote,use-quote,QuoteSheet}*`, listing route and tests | `cd frontend && pnpm vitest run src/features/payment` | Quote endpoints in the contract; ADR 0014 | #26, #50 |
| T3-02 | 3 | UC8: show paginated order history on `/account`; refund status remains on the receipt. | `FE/src/features/order/`, account route and tests | `cd frontend && pnpm gen:api && pnpm vitest run src/features/order src/routes/account.test.tsx` | `GET /orders/me` merged via #149 | #128, #31 |
| T3-03 | 3 | UC8: My blocs rows and Active / Completed / Refunded filters; show "refund due" on closed rows, not a paid-out claim. | `FE/src/features/blocs/`, account route and tests | Source-branch tests and review in #210; see that PR for verification. | Completed in #210 after owner-scoped `GET /group-deals/me` merged | #31 |
| T3-04 | 3 | UC4: JOIN purchase intent, disabled full/ended actions and explicit refusal reasons before payment. | Deal progress, checkout, listing route and tests | `cd frontend && pnpm vitest run src/features/catalog/DealProgress.test.tsx src/features/payment/payment.test.tsx` | Backend #27 completed | #27, #44 |
| T3-05 | 3 | UC7: share link/reference carried into JOIN intent, with copy/share UI. | `FE/src/features/share/`, product search, listing route and tests | `cd frontend && pnpm vitest run src/features/share src/features/catalog/products-search.test.ts` | Completed 2026-09-26; invite endpoint and `IntentRequest.ref` available | #30 |
| T3-06 | 3 | Receipt and return pages use the §4.5 card, buyer-order listing link, state-specific status and refund copy. | Payment routes, `receipt-view.ts`, `ErrorCard`, tests and design-screens §4.6 | `pnpm check` (32 files, 232 tests) and `pnpm build` passed on the source branch; live-backend check not run. | Q23–Q26 answered 2026-09-24 | #160 |

### Blocker status relevant to remaining work

| Blocker | Current status | Effect on this plan |
|---|---|---|
| B1 OAuth callback redirect | Resolved by backend: success redirects to `/auth/callback`; failures redirect to `/login?error=...`. | T2-04 implementation uses this flow. |
| B2 cookie security scheme | Resolved in the contract: `blocbuy-token` is an `apiKey` in cookie. | Regenerated client consumes the corrected contract. |
| B3 secure cookie on EC2 | Still depends on confirming HTTPS-only dev access. | Does not block local frontend work; remains a deployment/NFR item. |
| B4 `/auth/me` enrichment | Resolved: `{userId, nickname, avatarUrl}`. | Used by T2-04/T2-05. |
| B5 Google redirect configuration | Signed-in dev Playwright run passed on 2026-09-24 using a user-seeded storage state. | A fresh run still requires a valid session; the final sign-out test invalidates it. |
| A1 SPA fallback | Deployed 2026-09-22; live deep links return the SPA. | Imported by T2-07. |
| A2 security headers | Attached 2026-09-22; tightened policy reapplied and verified on dev 2026-09-24; CSP/HSTS observed on `/` again 2026-09-28. | T2-11 live header check recorded; repeat E2E if policy changes. |
| A3/A4 deploy role and environment | Resolved 2026-09-23: OIDC subject verified, `frontend-dev` restricted to `dev`, deploy switch configured. | CI run #165 passed package, deploy and dev E2E at `fbb71a9`. |

---

## 4. Task details

Completed tasks record the implementation that now exists rather than the original step-by-step scaffolding instructions. Source files, ADRs, CI configuration and runbooks are authoritative for exact implementation. Unfinished tasks retain executable steps.

### T0-00: Commit the plan and ADR stubs

**Status:** Complete.

The implementation plan and ADR series were created. ADRs 0001–0013 are now accepted and indexed in `frontend/docs/adr/README.md`.

### T1-01: Day-1 probe of the dev CloudFront

**Status:** Complete.

The initial probe established the deployment gaps that drove A1–A4: SPA deep links were not yet rewritten, the frontend had not been deployed, and backend/edge configuration still needed operator work. Results were moved into the Sprint 2 evidence trail rather than kept as executable setup instructions here.

### T1-02: File the blocker issues

**Status:** Complete.

Backend asks B1–B5 and AWS/tooling asks were split into tracked issues. B1, B2 and B4 were subsequently resolved by the backend; B3 and the B5 console checks remained operational follow-ups. See `docs/backend-blocker-response-20260916.md`.

### T1-03: Runbook steps and policy examples

**Status:** Complete.

The dev CloudFront runbook now owns the operational procedures that were originally embedded in this plan:

- §14: mandatory SPA fallback CloudFront Function;
- §15: CSP/HSTS response-headers policy;
- §16: frontend GitHub OIDC deploy role;
- §17: `frontend-dev` environment and variables;
- §10: pnpm build and S3/CloudFront release commands.

Versioned policy/function sources live under `deployment/frontend/`. Do not duplicate those commands here; use `docs/deploy/dev-cloudfront-deployment.md`.

### T1-04: Runtime and package-manager pins

**Status:** Complete. **ADR:** 0013.

The frontend pins Node 24 and pnpm 12.4.2 using `.nvmrc`, `.mise.toml`, `packageManager`, `engines`, `engine-strict`, and `save-exact`. pnpm's strict layout is intentional. `pnpm-workspace.yaml` allowlists package build scripts that must run non-interactively.

### T1-05: Scaffold

**Status:** Complete. **ADRs:** 0001, 0010.

Implemented the React 19/Vite 8 CSR scaffold with TanStack Router file-based routing, Tailwind 4, HeroUI 3, React Compiler, and the local `/api` proxy. The router plugin runs before React; `autoCodeSplitting` is explicit; `src/routeTree.gen.ts` is committed generated output and excluded from lint/format/dead-code checks.

React Compiler uses `@rolldown/plugin-babel` with `reactCompilerPreset()` and `babel-plugin-react-compiler`; the native compiler path remains T2-08.

Verification: `cd frontend && pnpm build`.

### T1-06: Generated API client

**Status:** Complete. **ADR:** 0003.

`@hey-api/openapi-ts` generates the fetch client, valibot response schemas, TanStack Query factories and MSW handlers into gitignored `src/api/generated/`. `src/api/hey-api.ts` forces `baseUrl: '/'` and same-origin credentials. `pnpm gen:api` runs during `prepare`, `check` and `build`.

The generator is pinned to `0.0.0-next-20260824173136` because the released 0.99.0 depended on the classic TypeScript compiler API removed from TypeScript 7. Generated files are stamped `// @ts-nocheck` and treated as library output.

Verification: `cd frontend && pnpm gen:api && pnpm typecheck`.

### T1-07: Envelope unwrap, error type, 401 interceptor, QueryClient

**Status:** Complete. **ADR:** 0009.

`unwrapResult` is the single boundary for backend `Result<T>` envelopes. Success is `COMMON_200_000`; failures become `ApiError` with HTTP status, backend code and `traceId` where available. Queries use generated option factories with `select: unwrapResult`.

The response interceptor throws on non-2xx responses. A 401 settles the `me` query to an anonymous success envelope and never redirects; route guards own navigation. Query defaults use a 30-second stale time and do not retry 401/404 `ApiError`s.

Verification: `cd frontend && pnpm vitest run src/api`.

### T1-08: Quality toolchain and `pnpm check`

**Status:** Complete. **ADRs:** 0006, 0011.

The local gate is oxlint with type-aware rules, oxfmt, TypeScript, Vitest/Testing Library/happy-dom, generated MSW handlers, knip and Lefthook. Generated API output and `routeTree.gen.ts` are excluded where appropriate.

`pnpm check` remains the canonical local gate; §5 lists the chain.

### T1-09: TanStack lint plugins spike

**Status:** Complete. **ADR:** 0006.

The oxlint JS-plugin path succeeded. `@tanstack/eslint-plugin-query` and `@tanstack/eslint-plugin-router` are hosted directly by oxlint; the planned ESLint fallback was not needed. ESLint may appear transitively because the plugins declare it as a peer, but it is not configured or executed.

The accepted risk is that oxlint JS plugins are alpha. If a future oxlint upgrade breaks them, the minimal ESLint fallback described in ADR 0006 is the escape hatch.

### T1-10: Agent configuration

**Status:** Implemented; manual hook smoke remains useful. **ADR:** 0012.

`frontend/AGENTS.md` is the shared convention file and `frontend/CLAUDE.md` imports it. Root `.claude/settings.json` merges frontend hooks with existing repository hooks. Path scoping is enforced by hook conditions and scripts.

TanStack Intent successfully discovers transitive `@tanstack/router-core` under pnpm; no direct dependency was added. The effective manifest is committed at `frontend/docs/tanstack-skills.json`.

The manual PostToolUse formatting, Stop typecheck and SessionStart client-regeneration smoke was moved to `backlog.md` FB-21 on 2026-09-28.

### T1-11: First component test

**Status:** Complete. **ADR:** 0011.

The initial `Header` test proved the React/Query/MSW/Testing Library loop. Shared route-test helpers now provide memory history and the application QueryClient rather than duplicating wrappers.

### T1-12: `.github` changes

**Status:** Complete.

Frontend CI is implemented. `frontend-check` generates the client, type-checks, lints, verifies formatting, tests, checks dead code and builds. `SonarCloud Frontend` is a separate required quality-gate context and produces coverage from Vitest LCOV. Security and Dependabot configuration include frontend paths/dependencies.

The current CI shape is documented in `docs/ci/backend-pipeline.md` and `docs/ci/devsecops-pipeline-plan.md`; those files, not this plan, own exact workflow behavior.

### T1-13: Playwright smoke

**Status:** Complete. **ADR:** 0011.

Playwright runs against local `vite preview` by default and can target the dev CloudFront with `E2E_TARGET=dev` and `E2E_BASE_URL`. Dev-only checks cover the SPA fallback and backend health when the corresponding infrastructure is available. Traces are captured on first retry.

### T1-14: First deploy

**Status:** Implemented in CI/runbook; environment execution is operator-controlled. **ADRs:** 0004, 0005, 0007.

The deploy path promotes one built `dist/` artifact, uploads hashed assets with immutable caching, uploads `index.html` with `no-cache`, then invalidates `/index.html`. Automated deployment is gated by the `frontend-dev` environment and `ENABLE_S3_DEPLOY`; the runbook remains the manual fallback.

### T1-15: ADR bodies and sprint evidence

**Status:** Complete. **ADR:** 0008.

ADRs 0001–0013 are accepted. Repo-wide sprint evidence lives under `docs/agile/`, not a frontend-only log. NFR evidence belongs in `docs/quality/nfr-register.md`.

### T2-01: Products list

**Status:** Complete.

The catalog follows the Claude Design artboards and `frontend/docs/design-screens.md` rather than the earlier placeholder layout. Key implementation outcomes:

- `productsSearchSchema` validates URL state; default page size is 20.
- The route loader warms the generated `pageListingsOptions` query and redirects out-of-range pages to the last valid page.
- Cards use the designed bloc/solo price hierarchy, image fallback, responsive 1/2/4-column grid, skeleton/error/empty states and router-based pagination.
- `formatPrice` prints SGD as `S$<amount>` with two decimals.
- Browser MSW is opt-in with `?msw=1` and uses seeded catalog data.
- The light HeroUI token overrides and Inter typography are implemented in `src/styles/app.css`/`index.html`.

Verification: `cd frontend && pnpm check`.

### T2-02: Product detail

**Status:** Complete.

The detail route uses generated `getListingOptions` and the buyer-detail/error artboards. It includes the responsive gallery, price hierarchy, deal progress, countdown/deadline formatting, trust copy, loading/error/not-found states and mobile sticky actions.

Important constraints discovered during implementation:

- the contract exposes only the best open deal, not the full team list or leader profile;
- product status is not available, so the designed off-sale state cannot yet be derived;
- JOIN now uses a purchase intent; full and ended blocs are disabled with a reason (T3-04). Signed-in Start shows the quote sheet (T3-01);
- invalid deadlines are treated as absent rather than passed to `Intl.DateTimeFormat`;
- 404 handling accepts either HTTP 404 or a backend code matching `_404_`.

Verification: `cd frontend && pnpm vitest run src/features/catalog && pnpm check`.

### T2-03: Listing filters

**Status:** Complete.

The catalog uses the artboard's single-select category chips plus a compact HeroUI `SearchField`. Category and keyword are URL state and reset the page to 1. Keyword is live in the backend contract, trimmed and capped at 64 characters; clearing it preserves the selected category.

The category dictionary is not in the contract, so `categories.ts` maps UI labels to the current backend seed values. Household maps to `home`; categories without seed rows legitimately render the empty state.

Verification: `cd frontend && pnpm check`.

### T2-04: Session, login, callback route, guard, account page

**Status:** Complete. **ADR:** 0002.

Authentication is implemented around the backend's HttpOnly `blocbuy-token` session:

- `useSession()`/`sessionFromQuery()` wrap generated `meOptions()` and produce authenticated/anonymous session state.
- Anonymous catalog browsing does not fail when `/auth/me` is unavailable; `/auth/callback` remains strict because it must confirm the new session.
- `/login` is a dedicated screen. Starting Google login stores a validated same-origin return path in `sessionStorage['blocbuy.returnTo']`, then performs a top-level navigation to `/api/v1/auth/oauth2/google`.
- The backend redirects successful OAuth callbacks to `/auth/callback`; the route refetches `me` and restores the saved return path. Backend failures land on `/login?error=<code>`.
- `_authenticated` protects `/account` and redirects anonymous users to `/login?returnTo=...`.
- `GatedActionSheet` handles anonymous paid actions on product detail.
- The header uses the artboard's neutral Sign in control; authenticated state shows avatar plus `nickname ?? userId`.
- `/account` shows My blocs above order history (T3-02/T3-03); closed rows do not claim a refund was issued without payment-level evidence.

This replaces the original plan's `/?redirect=` approach; the backend does not preserve an arbitrary return path through Google OAuth, so `sessionStorage` is the frontend bridge.

Verification: `cd frontend && pnpm check`.

### T2-05: Logout

**Status:** Complete. **ADR:** 0002.

`useLogout` uses the generated `logoutMutation`, validates the `ResultVoid` envelope, then writes the same anonymous success envelope used by the 401 interceptor into the `me` query before navigating home. This avoids an immediate refetch/401 round trip.

The temporary signed-in header uses a neutral `Sign out` button; a future design may replace it with the proposed avatar menu. Logout errors preserve the session and render an alert.

### T2-06: Login e2e

**Status:** Implemented; signed-in dev run passed 2026-09-24. **ADR:** 0011.

`e2e/login.spec.ts` owns the journey:

- the anonymous describe always runs, including against `vite preview`; it verifies the `/account` guard, the Google-login top-level navigation and parked return path, and the anonymous gated-action sheet;
- the signed-in `@auth` describe uses `e2e/.auth/dev.json`, skips when that file is absent, and only runs against the dev target;
- the storage state is created by a real Google login and is intentionally gitignored; the final sign-out test invalidates it, so it must be reseeded before another signed-in run.

The user seeded `e2e/.auth/dev.json` through Google login on 2026-09-24. The signed-in dev run (`pnpm e2e --grep @auth --trace on` with `E2E_TARGET=dev` and the CloudFront base URL) passed 2 tests; the sign-out test invalidated the state and the subsequent first-test rerun failed as expected. Traces: [PR #122 discussion](https://github.com/PPsyrius/iss-groupbuy-team8/pull/122#discussion_r4094696351). To run again, seed a new session:

```bash
cd frontend
pnpm exec playwright codegen --save-storage=e2e/.auth/dev.json \
  https://d1b3np88z657cp.cloudfront.net/login
```

Then run:

```bash
E2E_TARGET=dev \
E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net \
pnpm e2e --grep @auth
```

### T2-07: Terraform import of the dev frontend edge

- [x] Terraform 1.16.4 and AWS provider 5.100.0 were validated offline and in the operator's CloudShell on 2026-09-25.
- [x] `imports.tf` adopted nine existing S3, CloudFront and IAM resources in separate remote state; private reviewed apply: 9 imports, 0 additions/changes/deletions, followed by a zero-change plan. Source merged in PR #190.
- [x] The origin header stays out of committed files; provider-boundary sensitivity and private-plan handling are documented in ADR 0007. `plan -generate-config-out` was unnecessary because hand-tidied declarations matched live resources.
- [ ] Review the post-import stable-distribution-ARN refactor before using a fresh checkout for later origin-secret changes; see ADR 0007 and the dev runbook. Never apply this stack from CI.

See ADR 0007 and `docs/deploy/dev-cloudfront-deployment.md` for the live-resource ownership model.

### T2-08: Native compiler spike (branch `spike/native-react-compiler`)

- [x] On 2026-09-23 the native compiler spike passed `pnpm check` and the Header tests; build was 350 ms versus 929 ms, with similar asset size. ADR 0010 retains the Babel carrier; spike code was not committed.

### T2-09: react-table evaluation (branch `spike/react-table-31`)

- [x] The ten-row prototype compared TanStack Table against plain HeroUI `Table`; ADR 0001 chooses the plain table for #31. The experimental code was reverted before commit.

### T2-10: Authored skills

- [x] `adr` and `api-client-regen` were authored; `agile-evidence-capture` was extended for daily files and PR evidence. Frontmatter and real commands were checked on 2026-09-23; see the Sprint 2 log.

### T2-11: CSP tightening

- [x] Dev headers and preview pages were probed. Self-hosted Inter removed the Google Fonts violation; the policy dropped `data:` while keeping `'unsafe-inline'` for React Aria's injected styles. ADR 0002 and the Sprint 2 log record the checked-in policy.
- [x] Policy reapplied and verified on dev 2026-09-24; root response still carried CSP and HSTS in the 2026-09-28 `curl -sSI` check. The preview probe alone was not treated as live evidence.

### Tier 3 follow-ups

**T3-01 — UC3 quote:** Implemented 2026-09-24. `QuoteSheet` displays a preview and expiry countdown, restores a saved quote by ID and offers a new quote when expired. START re-prices at intent creation; Stripe shows the final amount. The quote-ID question remains on #50 (ADR 0014).

**T3-02 — UC8 order history:** Implemented 2026-09-24 using `GET /orders/me`, paginated on `/account` with no refund label (the receipt owns refund state). The option-B decision was posted on #128 on 2026-09-24; #128 closed 2026-09-25.

**T3-03 — UC8 My blocs:** Implemented in #210. `/account` fetches the latest 100 owner-scoped teams once and filters Active / Completed / Refunded chips client-side without another query; when more than 100 exist it says only the latest 100 are shown. Closed teams say "refund due" because `GET /api/v1/group-deals/me` provides no payment or refund state. Starter metadata and a paginated team list remain out of this slice (see §12 note 50).

**T3-04 — UC4 JOIN:** Implemented 2026-09-24. JOIN creates a purchase intent; the backend refuses closed, already-joined, full or missing blocs before checkout. The frontend shows the specific reason, says this attempt was not charged and refetches the listing; full/ended actions are disabled. An after-payment admission failure is still handled by receipt/refund, not by this preflight (ADR 0014).

**T3-05 — UC7 sharing:** Implemented 2026-09-26. The listing accepts validated `deal`/`ref` invite links, parks the reference for JOIN in `sessionStorage`, and displays an invite landing or closed-bloc recovery. Signed-in members can mint/copy/share links on the listing and expandable Active account rows; see `design-screens.md` §§4.2 and 4.4. Invalid newer links clear the parked reference; a replacement JOIN intent retains a valid one.

**T3-06 — receipt and return:** The design owner answered Q23–Q26 on 2026-09-24. The receipt uses the §4.5 terminal card, with payment-state copy in `receipt-view.ts`, amount and optional buyer-order `paidTime`, and BlocBuy reference rows. "See the listing" uses the buyer's order `productId` when available; the return fallback uses an `ErrorCard` with a neutral `Payment` chip. Source-branch `pnpm check` passed 232 tests and `pnpm build` passed; no real-backend receipt or shared deployment was checked.

---

## 5. The `pnpm check` chain

`frontend/package.json` owns the current scripts, including `hooks:install`; ADR 0006 records why the gate uses oxlint rather than the planned ESLint fallback. The `check` script runs these steps in order:

| Step | Command | Proves | Verified at |
|---|---|---|---|
| 1 | `pnpm gen:api` (`openapi-ts` plus the `// @ts-nocheck` stamp) | client, valibot, query options, MSW handlers regenerate from the committed contract | https://heyapi.dev/openapi-ts/configuration |
| 2 | `tsc --noEmit` | TypeScript 7 typecheck of `src`, `e2e`, config files | `typescript@7.0.2` `tsc` bin |
| 3 | `oxlint --type-aware` | lint incl. type-aware rules via `oxlint-tsgolint` | https://oxc.rs/docs/guide/usage/linter/type-aware |
| 4 | `oxfmt --check` | formatting, no writes | https://oxc.rs/docs/guide/usage/formatter/cli.html |
| 5 | `vitest run` | unit and component tests with generated MSW handlers | Vitest 5 |
| 6 | `knip` | no unused files, exports, dependencies (generated dirs excluded via `project`) | https://knip.dev/reference/configuration |

`prepare` runs on `pnpm install` so a fresh clone has the generated client before `tsc`. CI uses `pnpm install --frozen-lockfile` then `pnpm check`; `prepare` still runs under `--frozen-lockfile`.

## 6. Agent configuration

The original plan embedded proposed `AGENTS.md`, `CLAUDE.md`, `.claude/settings.json` and hook-script bodies here. Those files now exist and are the source of truth, so duplicating them in the plan would create drift.

Current ownership:

- `frontend/AGENTS.md`: frontend conventions and Intent-managed skill map;
- `frontend/CLAUDE.md`: imports `AGENTS.md`;
- `.claude/settings.json`: repository-level hooks and permissions, merged with the existing agile-evidence hook;
- `.claude/hooks/frontend-{fix,typecheck,bootstrap}.sh`: frontend-scoped automation;
- `frontend/docs/tanstack-skills.json`: normalized Intent discovery output.

See ADR 0012 for rationale and the T1-10 section for the remaining manual smoke.

## 7. CI and repository changes

The CI proposal in the original plan has been implemented and subsequently extended with SonarCloud. Exact workflow YAML is authoritative; this section records the intended boundaries.

### 7.1 Frontend CI

`ci-frontend.yml` provides `frontend-check` for generation, type checking, lint/format, tests, dead-code checks and build. It also provides the frontend SonarCloud analysis/coverage path documented in `docs/ci/backend-pipeline.md` and `docs/ci/devsecops-pipeline-plan.md`.

The repository ruleset currently treats `frontend-check` and `SonarCloud Frontend` as required contexts alongside the backend/security checks.

### 7.2 Security and dependency automation

`security.yml` includes frontend-relevant paths and dependency scanning. Dependabot has an npm ecosystem entry for `/frontend` with TanStack/HeroUI grouping. CODEOWNERS and the PR template own review/evidence routing.

Do not copy workflow bodies into this plan; update the workflow and its owning CI documentation together.

## 8. Dev CloudFront runbook

Detailed AWS commands and JSON policy bodies were removed from this plan after implementation because they are maintained in `docs/deploy/dev-cloudfront-deployment.md`. The relevant sections are:

- §10 frontend build/release convention;
- §14 mandatory SPA routing fallback;
- §15 security response headers;
- §16 frontend deploy OIDC role;
- §17 `frontend-dev` environment and variables.

Versioned CloudFront/IAM sources live under `deployment/frontend/`. T2-07 imported the dev edge into Terraform; T2-11's response-headers policy was reapplied and checked on dev on 2026-09-24.

## 9. Architecture decisions

The [ADR index](adr/README.md) is the canonical status record. Use ADR 0007 for edge ownership, ADR 0014 for payment behavior and the owning plan task for remaining follow-ups.

## 10. Blockers and external work

The original plan contained ready-to-file issue bodies. Those issues/documents now exist, so this section is a status index rather than a second copy of the issue tracker.

Backend status is recorded in `docs/backend-blocker-response-20260916.md`: B1, B2 and B4 were implemented; B3's cookie `Secure` flags remain a backend follow-up (#96, `backlog.md` FB-22). B5 supported a signed-in dev Playwright run on 2026-09-24; a new run requires a fresh session state.

AWS operations are owned by the runbook and the A1–A4 issues. T2-07 imported the existing dev edge on 2026-09-25; later CloudFront origin changes require the reviewed HCL and private plan described in ADR 0007.

## 11. TanStack skills manifest

Intent discovery is implemented and the machine-readable snapshot is `frontend/docs/tanstack-skills.json`. With the current exclusions, the useful frontend set includes router-plugin plus router-core skills such as auth/guards, data loading, navigation, not-found handling, path params, search params and type safety. pnpm transitive discovery works, so `@tanstack/router-core` is not a direct dependency.

The original long package/skill table is no longer duplicated here. Refresh the manifest with the project Intent command when TanStack dependencies change, normalize machine-specific paths as the existing workflow does, and update `AGENTS.md` through Intent rather than hand-editing its managed block. T2-09 separately evaluates the six skills shipped by `@tanstack/react-table`.

## 12. Implementation notes and deviations

The original plan accumulated 48 chronological deviation notes. Once a deviation became the implemented design, keeping both the old instruction and a later correction made the plan difficult to follow. Implemented outcomes have therefore been folded into T1/T2 sections above and into their owning ADRs.

### Stable §12 anchor index

Old `§12 note N` references in ADRs and reviews are stable anchors. Numbers are not reused; use this index to find where each note now lives.

| Anchor | Subject | Now lives |
|---|---|---|
| note 1 (§12.1) | React Compiler Babel carrier (`@rolldown/plugin-babel`, `@babel/core`) | T1-05, ADR 0010, `package.json` |
| note 2 | `ensureQueryData` deprecated, use `queryClient.query` | T2-01, ADR 0001 |
| note 4 | Dependabot pnpm 12 lockfile parsing unverified | ADR 0013 (open) |
| note 6 | oxlint React Compiler rules off by default, enabled by name | T1-08, ADR 0010 |
| note 8 | hey-api valibot plugin scope (`definitions: true`) | T1-06, ADR 0003 |
| note 9 | hey-api MSW plugin "In Progress", `createMswHandlers` | T1-06/T1-08, ADR 0003 |
| note 10 | HeroUI setup lighter than brief | T1-11, ADR 0001 |
| note 11 | `routeTree.gen.ts` committed; `autoCodeSplitting: true` explicit | T1-05, ADR 0001 |
| note 12 | Contract responses `*/*`, no generator change | ADR 0003 |
| note 16 | `routeTree.gen.ts` committed for fresh-clone `tsc` | ADR 0001 |
| note 17 (item 17) | Sprint log repo-wide, `agile-evidence-capture` skill | ADR 0008, `docs/agile/sprint-02/log.md` daily-log index |
| note 19 | hey-api repinned to `next` snapshot (TS 7, no compiler API) | T1-06, ADR 0003, `package.json` |
| note 27 | Header login control HeroUI `Link`, router-wrapped test | T1-11, ADR 0011 |
| note 28 | `pnpm-workspace.yaml` `allowBuilds: { msw: true }` committed | T1-06, ADR 0003 |
| note 31 | Intent skill count `>= 9` (excluded `ssr`) | T1-10, ADR 0012 |
| note 32 | Playwright `webServer` conditional-spread for `exactOptionalPropertyTypes` | T1-13, ADR 0011 |
| note 49 | Payment slice delivered outside the original Sprint 2 plan | Tier 3 rows, ADR 0014, `design-screens.md` §4.6 |
| note 50 | T3-03 My blocs presentation and bounded owner-scoped read | T3-03, #210; no refund-paid claim without payment data |

---

This plan remains the task-oriented record for the Sprint 2 frontend work and its Tier 3 follow-ups. Completed tasks record what was delivered; contract- and operator-gated steps remain explicit.
