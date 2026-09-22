<!-- intent-skills:start -->
# TanStack Intent - before editing files, run the matching guidance command.
tanstackIntent:
  - id: "@tanstack/router-core#router-core"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core"
    for: "Framework-agnostic core concepts for TanStack Router: route trees, createRouter, createRoute, createRootRoute, createRootRouteWithContext, addChildren, Register type declaration, route matching, route sorting, file naming conventions. Entry point for all router skills."
  - id: "@tanstack/router-core#router-core/auth-and-guards"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/auth-and-guards"
    for: "Route protection with beforeLoad, redirect()/throw redirect(), isRedirect helper, authenticated layout routes (_authenticated), non-redirect auth (inline login), RBAC with roles and permissions, auth provider integration (Auth0, Clerk, Supabase), router context for auth state."
  - id: "@tanstack/router-core#router-core/code-splitting"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/code-splitting"
    for: "Automatic code splitting (autoCodeSplitting), .lazy.tsx convention, createLazyFileRoute, createLazyRoute, lazyRouteComponent, getRouteApi for typed hooks in split files, codeSplitGroupings per-route override, splitBehavior programmatic config, critical vs non-critical properties."
  - id: "@tanstack/router-core#router-core/data-loading"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/data-loading"
    for: "Route loader option, loaderDeps for cache keys, staleTime/gcTime/ defaultPreloadStaleTime SWR caching, pendingComponent/pendingMs/ pendingMinMs, errorComponent/onError/onCatch, beforeLoad, router context and createRootRouteWithContext DI pattern, router.invalidate, Await component, deferred data loading with unawaited promises."
  - id: "@tanstack/router-core#router-core/navigation"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/navigation"
    for: "Link component, useNavigate, Navigate component, router.navigate, ToOptions/NavigateOptions/LinkOptions, from/to relative navigation, activeOptions/activeProps, preloading (intent/viewport/render), preloadDelay, navigation blocking (useBlocker, Block), createLink, linkOptions helper, scroll restoration, MatchRoute."
  - id: "@tanstack/router-core#router-core/not-found-and-errors"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/not-found-and-errors"
    for: "notFound() function, notFoundComponent, defaultNotFoundComponent, notFoundMode (fuzzy/root), errorComponent, CatchBoundary, CatchNotFound, isNotFound, NotFoundRoute (deprecated), route masking (mask option, createRouteMask, unmaskOnReload)."
  - id: "@tanstack/router-core#router-core/path-params"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/path-params"
    for: "Dynamic path segments ($paramName), splat routes ($ / _splat), optional params ({-$paramName}), prefix/suffix patterns ({$param}.ext), useParams, params.parse/stringify, pathParamsAllowedCharacters, i18n locale patterns."
  - id: "@tanstack/router-core#router-core/search-params"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/search-params"
    for: "validateSearch, search param validation with Zod/Valibot/ArkType adapters, fallback(), search middlewares (retainSearchParams, stripSearchParams), custom serialization (parseSearch, stringifySearch), search param inheritance, loaderDeps for cache keys, reading and writing search params."
  - id: "@tanstack/router-core#router-core/type-safety"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/type-safety"
    for: "Full type inference philosophy (never cast, never annotate inferred values), Register module declaration, from narrowing on hooks and Link, strict:false for shared components, getRouteApi for code-split typed access, addChildren with object syntax for TS perf, LinkProps and ValidateLinkOptions type utilities, as const satisfies pattern."
  - id: "@tanstack/router-plugin#router-plugin"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/router-plugin#router-plugin"
    for: "TanStack Router bundler plugin for route generation and automatic code splitting. Supports Vite, Webpack, Rspack, and esbuild. Configures autoCodeSplitting, routesDirectory, target framework, and code split groupings."
  - id: "@tanstack/virtual-file-routes#virtual-file-routes"
    run: "pnpm dlx @tanstack/intent@latest load @tanstack/virtual-file-routes#virtual-file-routes"
    for: "Programmatic route tree building as an alternative to filesystem conventions: rootRoute, index, route, layout, physical, defineVirtualSubtreeConfig. Use with TanStack Router plugin's virtualRouteConfig option."
<!-- intent-skills:end -->

# BlocBuy frontend: agent conventions

Read this before editing anything under `frontend/`. The plan is `docs/plan.md`; decisions are in `docs/adr/`. Settled decisions are not reopened in code.

## Commands

- `pnpm install` (runs `prepare` → generates `src/api/generated/`). Never commit that directory.
- `pnpm dev` (Vite on :5173, `/api` proxied to `http://localhost:8589`; start the backend with `docker compose up` at the repo root).
- `pnpm check` is the only gate: gen:api → typecheck → oxlint → oxfmt --check → vitest → knip. Run it before every commit and paste the result in the PR.
- `pnpm e2e` runs Playwright against a `vite preview` of `dist/` and runs `pnpm build` itself when `dist/index.html` is missing (an existing `dist/` is served as is, so rebuild after source changes); `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e` runs against dev.
- Git hooks: `pnpm hooks:install` (runs `lefthook install`) once per clone. Keep that script: `lefthook.yml` lives at the repo root, so it is the only reference knip can see for the `lefthook` devDependency.

## Stack (exact pins in package.json; do not add caret ranges)

React 19.3 CSR, Vite 8, TanStack Router (file-based via `@tanstack/router-plugin`), TanStack Query 5, TanStack Form 1, valibot, Tailwind CSS 4, HeroUI 3, TypeScript 7, oxlint + oxfmt, Vitest + Testing Library + happy-dom, MSW 2, Playwright.

## Layout

- `src/routes/` — one file per route, TanStack flat naming: `__root.tsx`, `index.tsx` (`/`), `products.$productId.tsx`, `_authenticated.tsx` (pathless guard layout) + `_authenticated/account.tsx`, `auth.callback.tsx`. Files starting with `-` are ignored by the route generator. `src/routeTree.gen.ts` is generated: commit it, never edit it.
- `src/features/<feature>/` — components, hooks, schemas, tests for one feature (`catalog`, `session`). Colocate tests as `Name.test.tsx` next to `Name.tsx`.
- `src/components/` — shared presentational components (`Header`).
- `src/api/` — `envelope.ts` (Result unwrap, `ApiError`), `client.ts` (401 interceptor), `query-client.ts`, `hey-api.ts` (runtime config, `baseUrl: '/'`), `generated/` (gitignored output).
- `src/test/` — Vitest setup and MSW server (`msw.ts`), browser MSW behind `?msw=1` (`browser-msw.ts`).
- `e2e/` — Playwright specs; `e2e/.auth/*.json` is local-only.

## Rules

1. Data comes from the generated client only: `pageListingsOptions()`, `getListingOptions()`, `meOptions()`, `logoutMutation()` from `src/api/generated/@tanstack/react-query.gen.ts`. Always pass `select: unwrapResult` (or a function that calls it). Never hand-write a response type or schema; regenerate with `pnpm gen:api` when `contracts/openapi.json` changes. What the schema cannot express — status vocabularies, required headers, call ordering, which backend capabilities have no endpoint yet — is in [`docs/api/backend-integration-guide.md`](../docs/api/backend-integration-guide.md).
2. valibot appears in exactly two places: `validateSearch` schemas (`src/features/<feature>/*-search.ts`) and form schemas (`src/features/<feature>/*.schema.ts`).
3. HeroUI components over raw React Aria Components. Check the HeroUI docs (https://heroui.com/docs/react) for prop names; do not guess.
4. React Compiler is on. No `useMemo`, `useCallback`, or `React.memo`. oxlint's `react/*` compiler rules fail the build on violations.
5. No `import.meta.env`, no `VITE_*` variables, no absolute API URLs. All requests are relative `/api/...`. CI greps for `import.meta.env` and fails.
6. Auth: `useSession()` is the only source of truth. On 401 the response interceptor in `src/api/client.ts` settles the `me` query as anonymous — `setQueryData(meQueryKey(), { code: OK_CODE, message: 'anonymous' })`, so `unwrapResult` yields `undefined` and the header falls back to the login link. It never removes, invalidates or resets that query (the `me` request's own 401 would loop) and it never redirects; only route `beforeLoad` guards redirect. Login is a full-page navigation to `/api/v1/auth/oauth2/google`.
7. Route loaders warm the cache with `context.queryClient.query({ ...options, staleTime: 'static' })`; components read with `useSuspenseQuery(options)`. `ensureQueryData` is deprecated; do not use it.
8. Tests: Vitest + Testing Library; network is mocked by the generated MSW handlers in `src/test/msw.ts`; override per test with `server.use(http.get('*/api/v1/...', ...))`. Playwright covers only the smoke and login journeys.
9. Never edit generated code: `src/api/generated/**`, `src/routeTree.gen.ts`.
10. Commits: Conventional Commits with scope `frontend` (`feat(frontend): ...`). Branch `feature/<slug>`. PRs target `dev`, link the issue, name the ADR(s), include `pnpm check` output or the CI link, and attach a screenshot or Playwright trace for UI changes.
11. Before adding a dependency: exact pin, explain in the PR why an existing one does not cover it, run `pnpm knip`.

## Skills

- TanStack (via Intent): the `intent-skills` block at the top of this file is maintained by `pnpm dlx @tanstack/intent@0.4.0 install --map` from `frontend/` (the tool writes it there, not at the end); do not hand-edit it. The allowlist and exclusions live in `package.json#intent`. Router skills come from `@tanstack/router-core` (`auth-and-guards`, `search-params`, `data-loading`, `navigation`, `path-params`, `not-found-and-errors`, `code-splitting`, `type-safety`) and `@tanstack/router-plugin`. Query and Form ship no skills today; use the official docs linked in `docs/plan.md`.
- Repo skills (root `.claude/skills/`): `agile-evidence-capture` — run it alongside any change here, it owns the sprint log, the NFR register and the ADR paper trail. `backend-constraints-skill` and `pgsql-ddl` are backend-only; do not load them for frontend work. `adr` and `api-client-regen` land with T2-10.
