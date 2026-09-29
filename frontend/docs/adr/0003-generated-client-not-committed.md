# 0003. Generated API client, not committed

Date: 2026-09-16

## Status

Accepted

## Context

`contracts/openapi.json` (OpenAPI 3.1) is exported by the backend's `openapi-export` Maven profile and committed; the backend owns it, and `ci-backend`'s `contract-check` already fails on breaking changes without the `contract-change-approved` label. Every JSON response is a `Result<T>` envelope (ADR 0009). The committed spec's `servers[0].url` is the export harness `http://localhost:18080`, and every response content type is `*/*` rather than `application/json` (plan §12 note 12). Hand-written response types would drift from the contract; committed generated code would drift the other way and bury reviews under regenerated diffs.

## Decision

`@hey-api/openapi-ts` generates, at install time and into the gitignored `src/api/generated/`, the fetch client (`@hey-api/client-fetch`, runtime config `src/api/hey-api.ts` with `baseUrl: '/'`), valibot schemas (`responses: true`, `definitions: true`, `requests: false`, note 8), TanStack Query `queryOptions`/`queryKeys`/`mutationOptions`, and MSW handlers. `pnpm gen:api` runs in `prepare`, first in `pnpm check` and first in `pnpm build`; the output is excluded from oxlint, oxfmt, knip and coverage. Components consume only the generated option factories with `select: unwrapResult`; nobody hand-writes a response type or schema. The `*/*` content type needs no generator change, because the fetch client parses by the runtime `Content-Type` header (Spring sends `application/json`); if that ever misbehaves the fix is `produces` on the backend controllers.

Alternatives rejected: commit the generated code (drift and review noise, contract already enforced by `contract-check`); hand-written fetch wrappers (types drift); orval or openapi-typescript (no valibot + query + msw trio).

2026-09-16: `@hey-api/openapi-ts` is pinned to the exact prerelease `0.0.0-next-20260824173136`, not the brief's `0.99.0` — `0.99.0` uses the classic TypeScript compiler API, which `typescript@7.0.2` no longer ships (upstream hey-api/hey-api#4235, confirmed open); the `next` snapshot has no `typescript` dependency and needed no config changes (T1-06, `760d4d8`). Generated files are stamped `// @ts-nocheck` by `frontend/scripts/ts-nocheck-generated.mjs` in `gen:api`, so `tsc` treats `src/api/generated/**` as library code and `exactOptionalPropertyTypes: true` stays project-wide (T1-08, `f811662`). The generated MSW export is `createMswHandlers().all()`, not `getAllMocks` (T1-06/T1-08).

## Consequences

A fresh clone needs `pnpm install` (network) before `tsc` or the editor sees any API type; CI runs `pnpm install --frozen-lockfile`, under which `prepare` still runs, and the `SessionStart` hook regenerates a missing client for agent sessions (ADR 0012). `frontend/pnpm-workspace.yaml` with `allowBuilds: { msw: true }` is committed because pnpm 12 otherwise refuses msw's postinstall and a non-interactive install fails (note 28, `760d4d8`). The MSW plugin is labelled "In Progress" upstream (note 9); the planned fallback of hand-registering the four skeleton handlers in `src/test/msw.ts` was not needed. Owed: repin to a released `@hey-api/openapi-ts` once one ships without the TypeScript 6 compiler-API dependency; until then Dependabot cannot bump it sensibly. Under B2 (#95) the contract still declares the `blocbuyToken` scheme as `in: header`; the generated `security` typing is ignored and the browser sends the cookie, so the only action when B2 lands is `pnpm gen:api`. `// @ts-nocheck` hides type errors inside generated files; that is the intended "generated = library code" treatment, and generator upgrades are reviewed through the `pnpm check` gate, not by reading the output.

2026-09-29 implementation note: B2 (#95) landed; the committed contract declares cookie-based `blocbuy-token` authentication and the client was regenerated. The earlier B2 sentence records the original planning state, not a pending contract change (see ADR 0002 and `contracts/openapi.json`).
