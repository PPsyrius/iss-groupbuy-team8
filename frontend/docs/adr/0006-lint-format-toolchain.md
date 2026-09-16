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
