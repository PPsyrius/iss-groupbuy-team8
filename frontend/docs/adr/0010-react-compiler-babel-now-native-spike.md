# 0010. React Compiler via Babel preset now, native path as spike

Date: 2026-09-16

## Status

Accepted

## Context

The blueprint settled React Compiler ON with the convention of no manual `useMemo`, `useCallback`
or `React.memo`, naming `@vitejs/plugin-react` 6 "with Babel" and `babel-plugin-react-compiler`
1.0.0 as the carrier. On the planning day the plugin's README no longer documented a
`babel.plugins` option: the documented Babel path is `@rolldown/plugin-babel` with the
`reactCompilerPreset()` that `@vitejs/plugin-react` exports, with `@babel/core` and
`babel-plugin-react-compiler` as optional peers (plan §12 note 1). The plugin's own
`react({ compiler: true })` path runs the compiler natively through `oxc-transform-react` and is
labelled experimental; `@vitejs/plugin-react-oxc` is deprecated. Vite 8 is Rolldown-based, so a
Babel pass is the one JavaScript transform left in an otherwise native pipeline. oxlint's `react`
plugin ships the compiler's diagnostics as rules, but they are experimental and off by default
(note 6).

## Decision

`vite.config.ts` keeps `react()` and adds `babel({ presets: [reactCompilerPreset()] })` from
`@rolldown/plugin-babel` 0.2.4, with `@babel/core` 8.0.5 and `babel-plugin-react-compiler` 1.0.0
pinned exactly; the router plugin stays first, Tailwind last. The compiler's rules are enabled by
name in `.oxlintrc.json` (`react/rules-of-hooks`, `exhaustive-deps`, `hooks`, `purity`,
`immutability`, `refs`, `globals`, `exhaustive-effect-dependencies`, all `error`) so `pnpm check`
fails on code the compiler would bail out of. The native path is a tier-2 spike (T2-08, branch
`spike/native-react-compiler`): `react({ compiler: true })` with `oxc-transform-react` 0.150.0,
comparing `pnpm check`, `pnpm build` output size (`du -sh dist/assets`) and the Header test
against `dev`; it is adopted only through a dated addendum to this ADR, never merged as code from
the spike branch.

Alternatives rejected: the native compiler as the baseline (experimental, and its diagnostics
behaviour is unverified on this codebase); no compiler with a manual memoisation convention
(agents forget it and no lint rule can prove it was followed).

## Consequences

Same decision as the blueprint, different carrier: two extra pins (`@rolldown/plugin-babel`,
`@babel/core`) and a Babel pass on every build. Measured cost is small on this tree: `vite build`
took 548 ms for 240 modules at T1-05 (2026-09-16) and 616 ms at T1-13 (2026-09-17). Because
`reactCompilerPreset()` is a Babel preset applied by a separate plugin, `react()` still handles
Fast Refresh and the automatic JSX runtime (`jsx: react-jsx`), which is why
`react/react-in-jsx-scope` is off (ADR 0006). A component the compiler bails out of stays
unmemoised silently unless a lint rule catches it; the enabled rules are the guard. Bumping
`babel-plugin-react-compiler` past 1.0.x is a Dependabot PR reviewed like any other pin (ADR 0013).

Addendum (T2-08, 2026-09-23, spike on the working tree at `12045be`, reverted): `react({ compiler:
{ logDiagnostics: true } })` with `oxc-transform-react` 0.150.0 (0.151.0 is current) replacing the
Babel pass. `pnpm check` exit 0 (25 files / 163 tests, oxlint compiler rules unchanged, knip did not
flag the three idle Babel pins); Header test 5/5; `vite build` 350 ms vs 929 ms; `dist/assets`
1356 KB vs 1352 KB (JS 638,828 B vs 637,250 B); unminified builds reference `useMemoCache` 7 times
on both paths; no diagnostics logged. Decision: keep the Babel preset. The native path saves about
0.6 s per build and nothing at runtime, is still labelled experimental, and swapping carriers
costs a fourth pin; revisit when `@vitejs/plugin-react` drops the experimental label.
