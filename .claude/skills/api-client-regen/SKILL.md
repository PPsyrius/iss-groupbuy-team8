---
name: api-client-regen
description: Use when contracts/openapi.json changed or is about to change, when a PR mentions the contract, the generated client, gen:api, hey-api, openapi-ts, a removed or renamed endpoint, or when the frontend fails typecheck with "has no exported member" from @/api/generated. Frontend only.
---

# Regenerate the API client after a contract change

## How the client is wired

- Source of truth: `contracts/openapi.json` (committed by the backend, guarded by the `contract-check` job and the `contract-change-approved` label in `ci-backend.yml`).
- Generator: `frontend/openapi-ts.config.ts` runs `@hey-api/openapi-ts` with the fetch client (`runtimeConfigPath` = `src/api/hey-api.ts`, `baseUrl: "/"`, `credentials: "same-origin"`), valibot response schemas, TanStack Query options and MSW handlers.
- Output: `frontend/src/api/generated/` (gitignored, ADR 0003). `pnpm gen:api` also stamps every file with `// @ts-nocheck` (`scripts/ts-nocheck-generated.mjs`); lint, format and knip exclude the directory.
- It runs on `pnpm install` (`prepare`), `pnpm build` and `pnpm check`, so a stale checkout regenerates itself. A worktree that never ran `pnpm install` has no client at all.

## When the contract changes

1. Pull the contract: `git fetch origin && git log --oneline -3 origin/dev -- contracts/openapi.json`. Note whether the change is additive, a rename, or a removal (`git diff <old> <new> -- contracts/openapi.json | grep '^[-+] *"/api'`).
2. Regenerate: `cd frontend && pnpm gen:api`. Never edit or commit anything under `src/api/generated/`.
3. Find consumers of what moved. Generated names are `<operationId>Options`, `<operationId>Mutation`, `<operationId>QueryKey` from `@/api/generated/@tanstack/react-query.gen` and types from `@/api/generated`:

   ```bash
   grep -rn "<oldOperationId>\|/api/v1/<old-path>" src --include='*.ts' --include='*.tsx' | grep -v src/api/generated
   ```

   Removed operation with a consumer: the feature loses its data source; file or reference the issue (as #128 did for `GET /orders`) rather than pointing the UI at a guessed path.
4. Wire new operations through the generated options only, always with `select: unwrapResult` (or a function that calls it) from `src/api/envelope.ts`; route loaders warm the cache with `context.queryClient.query({ ...options, staleTime: 'static' })`, components read with `useSuspenseQuery` (AGENTS.md rules 1 and 7). No hand-written response types.
5. Tests: `src/test/msw.ts` installs `createMswHandlers` from the generated `msw.gen`, so new endpoints get default handlers for free; override per test with `server.use(http.get('*/api/v1/...', …))`. A removed endpoint makes its `server.use` overrides dead code; delete them.
6. Gate: `pnpm check` (gen → typecheck → oxlint → oxfmt → vitest → knip). Typecheck errors of the form `Module '"@/api/generated"' has no exported member 'xOptions'` are the list of type breaks; fix each consumer, do not re-export shims.
7. Evidence: PR body "Contract" line (`unchanged` / `regenerated (additive)` / `breaking, contract-change-approved requested because …`), and the day's sprint log entry names the operations touched.

## Quick reference

| Question | Answer |
|---|---|
| Which operations exist? | `jq -r '.paths | to_entries[] | .key as $p | .value | to_entries[] | "\(.key | ascii_upcase) \($p) \(.value.operationId)"' contracts/openapi.json` |
| Which are consumed? | grep the `Options`/`Mutation` names under `src` excluding `src/api/generated` |
| Contract on `dev` differs from my branch? | `git diff origin/dev -- contracts/openapi.json`; rebase before regenerating |
| Error code for a 4xx? | `PaymentErrorCode`-style enums live in the backend; the contract carries `code` strings in `Result` responses; `ApiError.code` from `envelope.ts` exposes them |

## Common mistakes

- Committing `src/api/generated/` or `routeTree.gen.ts` edits by hand.
- Calling `fetch('/api/…')` or building a URL because the generated option "does not exist yet": it does not exist because the contract does not have it; the backend PR must land first.
- Regenerating against a local backend's live spec instead of the committed contract: the committed file is what CI and `contract-check` see.
- Forgetting the ts-nocheck stamp: run `pnpm gen:api`, not `openapi-ts` directly.
