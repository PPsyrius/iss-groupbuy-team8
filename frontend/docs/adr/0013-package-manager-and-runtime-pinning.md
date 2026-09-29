# 0013. Package manager and runtime pinning

Date: 2026-09-16

## Status

Accepted

## Context

Five people, several coding agents and CI all install the same tree, and the walking skeleton is due inside one sprint, so "works on my machine" differences in Node or package-manager version are a cost the team cannot absorb. pnpm 12's strict `node_modules` layout keeps undeclared imports from resolving, which matters when agents add imports. Corepack, the mechanism that honours `packageManager`, ships with Node 24 LTS and is no longer bundled from Node 25. The repository's dependency-update tool is Dependabot (`maven`, `github-actions` and `docker` entries already exist in `.github/dependabot.yml`). Blueprint pins were verified against the registry on 2026-09-15 and again on 2026-09-16.

## Decision

`frontend/package.json` carries `"packageManager": "pnpm@12.4.2"` and `"engines": { "node": ">=24 <25", "pnpm": "12.4.2" }`; `frontend/.npmrc` sets `engine-strict=true` and `save-exact=true`, so every dependency is an exact pin and a wrong runtime fails the install. Node 24 LTS is pinned by `frontend/.mise.toml` (`node = "24"`) and `frontend/.nvmrc` (`24`) for both tool managers. CI reads the same files: `pnpm/action-setup` with no `version` (it uses `packageManager`), `actions/setup-node` with `node-version-file: frontend/.nvmrc` and the pnpm cache keyed on `frontend/pnpm-lock.yaml`, then `pnpm install --frozen-lockfile`. Dependabot gets an `npm` ecosystem entry for `/frontend`, daily with a 7-day cooldown, groups `tanstack` (`@tanstack/*`) and `heroui` (`@heroui/*`, `react-aria-components`), ignoring major bumps of `react`, `react-dom` and `typescript`; that entry was added in T1-12 (`a300368`, PR-E).

Alternatives rejected: npm (slower, no strict `node_modules`); Node 25 (no bundled Corepack); Renovate (repo standard is Dependabot).

## Consequences

pnpm 12 refuses dependency postinstall scripts unless allowed, so `frontend/pnpm-workspace.yaml` carries `allowBuilds` for `msw` (T1-06, `760d4d8`) and `lefthook` (T1-08); any future package with a build script needs the same entry or `pnpm install --frozen-lockfile` fails non-interactively in CI. Corepack downloads the pinned pnpm on first use (`corepack pnpm --version` printed `12.4.2` in T1-04, 2026-09-16); `mise install` was not verified because mise is not installed on the author's machine, and `pnpm add` rewrites `engines` onto three lines (cosmetic). Two pins deviate from "exact release": `@hey-api/openapi-ts` is an exact `next` snapshot (ADR 0003), and `eslint@10.10.0` appears in the lockfile only as an auto-installed peer of the TanStack lint plugins (ADR 0006).

Open (plan §12 note 4): GitHub documents pnpm v7–v10 under the `npm` ecosystem; whether Dependabot parses a pnpm 12 lockfile is unverified. After PR-C merges, trigger "Check for updates" under Insights → Dependency graph → Dependabot. Fallback decision slot: if it fails, the choice is a `pnpm-lock.yaml` `lockfileVersion` downgrade versus waiting on GitHub, recorded here as a dated addendum.
