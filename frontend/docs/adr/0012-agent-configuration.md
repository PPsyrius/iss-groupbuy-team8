# 0012. Agent configuration

Date: 2026-09-17

## Status

Accepted

## Context

Most frontend work in this repo is done through coding agents. Without checked-in guardrails each session re-derives the same conventions (generated client only, no `import.meta.env`, no `useMemo` under React Compiler, never edit `src/api/generated/**`) and each one gets a different subset right. The repo is a polyglot monorepo: a root-level agent config that formatted or typechecked everything would fire on Java changes too. TanStack ships per-package agent skills that Intent discovers from `node_modules`, but pnpm's strict store puts transitive packages under `node_modules/.pnpm/...` rather than at the top level, and the router skills we want live in `@tanstack/router-core`, which is transitive here — worth verifying before depending on it.

## Decision

Conventions live in `frontend/AGENTS.md` (the cross-tool format); `frontend/CLAUDE.md` is one line, `@AGENTS.md`, so Claude Code reads the same file. Automation lives in the root `.claude/settings.json`, which was **merged**, not overwritten: the existing `PreToolUse` Bash hook from `agile-evidence-capture` stays, and §6.3's `$schema`, `permissions`, `PostToolUse`, `Stop` and `SessionStart` were added around it. Because `matcher` matches tool names only, path scoping to `frontend/**` is done with the per-handler `if` field plus a second check inside each script, so nothing fires on backend or infra edits. The three scripts are `.claude/hooks/frontend-{fix,typecheck,bootstrap}.sh`: format/autofix a touched frontend file, `tsc --noEmit` on Stop (exit 2 feeds the errors back), and regenerate the gitignored client on SessionStart.

Skills: TanStack skills are discovered at runtime by Intent, allowlisted through `frontend/package.json#intent.skills` (`@tanstack/*`) and narrowed by `intent.exclude` (`router-core/ssr` — this app is pure CSR per ADR 0001 — plus the devtools plugin-authoring skills). `intent install --map` owns the `intent-skills` block in AGENTS.md; it writes it at the top of the file, not the bottom as §6.1 assumed.

Transitive discovery under pnpm **works**: `intent list --json` finds `@tanstack/router-core` 1.171.30 with all 10 skills at `node_modules/.pnpm/@tanstack+router-core@1.171.30/...`, so the fallback of adding it as a direct dependency was not needed and the frontend keeps only `@tanstack/react-router` as its router dependency. The listing is committed as `frontend/docs/tanstack-skills.json` with absolute paths rewritten relative to `frontend/`, so the planned `intent-stale` CI diff (T1-12) is machine-independent.

## Consequences

Agent sessions that trust the folder get formatting, typechecking and client generation for free, and teammates who do not use Claude Code are unaffected — the same rules are prose in AGENTS.md. `.oxfmtrc.json` and `lefthook.yml` exclude AGENTS.md and CLAUDE.md from the formatter, since the Intent block must stay byte-identical to what the tool writes. Excluding `router-core/ssr` drops the listed skill count for that package from 10 to 9, so the T1-10 task-row acceptance test's `>= 10` became `>= 9` (plan §12 note 31). Intent runs from the network on every `pnpm dlx`, so an offline session simply gets no TanStack skills; nothing else breaks.
