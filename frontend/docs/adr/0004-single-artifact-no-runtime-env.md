# 0004. Single build artifact promoted dev to prod

Date: 2026-09-16

## Status

Accepted

## Context

The SPA is static files on S3 behind CloudFront; the API is the same origin at `/api/*` (ADR
0002), so the bundle has no environment-specific URL to carry. The runbook (§10) already prescribes
the release shape: hashed assets uploaded with `public,max-age=31536000,immutable`, `index.html`
with `no-cache`, then an invalidation of `/index.html`. Per-environment builds would mean the
artifact that passed `pnpm check` and the Playwright preview run is not the artifact that reaches
users, and `VITE_*` variables are the usual way that difference creeps in. The backend pipeline
already promotes one ECR image through environments; the frontend should match that model.

## Decision

One `dist/` is built once in CI by the `frontend-check` job (`pnpm check`, then `pnpm build`),
uploaded as the artifact `frontend-dist-<sha>`, and downloaded unchanged by `deploy-s3-dev` (and
by any later prod job). No `VITE_*` variable and no `import.meta.env` anywhere under `src/` or
`e2e/` (`frontend/AGENTS.md` rule 5; a "Forbid import.meta.env" CI step greps for both and fails on
any hit; the PR template carries the checkbox). All requests are relative `/api/...`. Any future
runtime configuration is a `/config.json` fetched at startup and served `no-cache`; its file
extension means the SPA fallback function passes it through untouched (ADR 0005). Deploys use
the three runbook commands (`s3 sync --delete --exclude index.html`, `s3 cp index.html`,
`create-invalidation /index.html`), and the manual fallback in runbook §10 uses the same three.

Alternatives rejected: per-environment builds with `VITE_*` (artifact ≠ tested artifact);
build-time API URL (breaks the same-origin rule and needs a rebuild per environment).

## Consequences

`grep -rn "import.meta.env\|VITE_" frontend/src frontend/e2e` is empty as of 2026-09-17 (T1-15
check); the "Forbid import.meta.env" step of `ci-frontend.yml` enforces it (T1-12, `a300368`, PR-E). `deploy-s3-dev` runs only on push to
`dev` with `vars.ENABLE_S3_DEPLOY == 'true'`, inside the `frontend-dev` environment, assuming the
OIDC role from runbook §16, so the first deploy (T1-14) waits on A3 (#101) and A4 (#102); the
fallback is the account holder running runbook §10 by hand with a `dist/` built at the merged
`dev` SHA. `--delete` on the sync means no previous build's hashed assets survive a release; if a
rollback build is wanted, drop `--delete` and add a lifecycle rule in T2-07.

2026-09-23: `ci-frontend` run #165 built `fbb71a9`, deployed that artifact through the scoped OIDC role and passed `e2e-dev`; A3/A4 no longer block this path.

Open, found in the 2026-09-17 branch review: the TanStack devtools are in the production bundle
and `build.sourcemap: true` publishes full source maps. Both fixes on the table (an
`import.meta.env.DEV` carve-out used only for dead-code elimination, or a lazy import) touch this
decision's "no `import.meta.env`" rule and the CI grep, so whichever is chosen is recorded here as
a dated addendum before T1-14; a DCE-only `DEV` check still yields one artifact per build, which
is the property this ADR protects.
