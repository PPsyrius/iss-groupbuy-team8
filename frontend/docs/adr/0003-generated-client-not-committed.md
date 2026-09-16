# 0003. Generated API client, not committed

Date: 2026-09-16

## Status

Proposed

## Context

## Decision

2026-09-16: `@hey-api/openapi-ts` is pinned to the exact prerelease `0.0.0-next-20260824173136`,
not the brief's `0.99.0` — `0.99.0` uses the classic TypeScript compiler API, which
`typescript@7.0.2` no longer ships (upstream hey-api/hey-api#4235, confirmed open); the `next`
snapshot has no `typescript` dependency and needed no config changes (T1-06, `760d4d8`). Generated
files are stamped `// @ts-nocheck` by `frontend/scripts/ts-nocheck-generated.mjs` in `gen:api`, so
`tsc` treats `src/api/generated/**` as library code and `exactOptionalPropertyTypes: true` stays
project-wide (T1-08, `f811662`). The generated MSW export is `createMswHandlers().all()`, not
`getAllMocks` (T1-06/T1-08).

## Consequences
