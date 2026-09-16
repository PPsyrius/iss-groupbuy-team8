# 0006. Lint and format toolchain

Date: 2026-09-16

## Status

Proposed

## Context

## Decision

2026-09-16: two `.oxlintrc.json` rule overrides beyond the brief, both evidenced against oxlint
1.83.0's schema: `react/react-in-jsx-scope` is `"off"` (no `jsxRuntime` setting exists to tell it
about the project's automatic JSX transform, so it false-positives on every JSX file);
`import/no-unassigned-import` stays `"error"` with `{ "allow": ["**/*.css",
"@testing-library/jest-dom/vitest"] }` rather than being turned off. `.oxfmtrc.json` and
`lefthook.yml` both exclude `docs/**` and `README.md` — hand-authored prose is not a formatter
target (T1-08, commits `c59dcf7`, `aa7b2a6`).

## Consequences

2026-09-17 (T1-09, the timeboxed TanStack lint spike): the oxlint **JS-plugins** path won, so the
plan's step-2 fallback (`eslint` 10.10.0 + `eslint-plugin-oxlint` 1.83.0 +
`frontend/eslint.config.js`) was not needed and no ESLint is configured or invoked anywhere in the
gate. oxlint 1.83.0 loads
`@tanstack/eslint-plugin-query` 5.102.8 and `@tanstack/eslint-plugin-router` 1.162.0 through the
`jsPlugins` key of `.oxlintrc.json`, under Node 24, with no flag, no parser and no error text —
the four rules named in the plan (`@tanstack/query/exhaustive-deps`,
`@tanstack/query/no-rest-destructuring`, `@tanstack/query/stable-query-client`,
`@tanstack/router/create-route-property-order`) are configured by those exact ids and report under
the namespaces `@tanstack/query` and `@tanstack/router` (oxlint strips the package name's
`eslint-plugin-` segment). Evidence: a fixture that closes over a variable the `queryKey` omits
fails `pnpm lint` with `@tanstack/query(exhaustive-deps): The following dependencies are missing
in your queryKey: id`, and a mis-ordered `createFileRoute` object fails with
`@tanstack/router(create-route-property-order)`; `pnpm check` on the real sources exits 0.

Costs. The gate keeps **one** linter: `pnpm lint` stays `oxlint --type-aware`, `pnpm check` keeps
its six steps, and there is no second config file, no second ignore list to keep in sync with
`src/api/generated/**` and `src/routeTree.gen.ts`, and no `eslint-plugin-oxlint` and no
`@typescript-eslint/parser`. One caveat, stated so nobody is surprised by the lockfile: both
TanStack plugins declare `eslint` as a peer dependency, and pnpm 12 auto-installs peers, so
`eslint@10.10.0` lands in `node_modules/.pnpm` (and in `pnpm-lock.yaml`) as a transitive package.
It is not a direct dependency, has no config file, is not on `PATH` as a project script, and is
never executed — oxlint hosts the rules itself. The two plugins are devDependencies referenced
only from `.oxlintrc.json`; knip
6.35.1 resolves them from that config and does not report them unused, so `knip.json`
`ignoreDependencies` is untouched. Runtime cost is a Node runtime spun up alongside the Rust
linter to host the plugin rules: on this tree `oxlint --type-aware` measured 0.246 s with the two
plugins removed and 0.310 s with them loaded (~60 ms), so the pre-commit hook is unaffected.

Risk accepted: oxlint's JS plugins are labelled **alpha and not subject to semver** (oxlint's own
configuration schema says so). A patch-level oxlint bump could therefore change or drop
`jsPlugins`. The escape hatch is unchanged and cheap — plan §4 T1-09 step 2 still describes the
minimal ESLint fallback, and adopting it would only add a `&& eslint .` to the `lint` script.
