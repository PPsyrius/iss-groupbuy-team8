## What

<!-- one paragraph; same intent as the Conventional Commit title with scope -->

## Issue

Closes #

## Decisions

ADR(s) implemented or affected: <!-- frontend: `frontend/docs/adr/NNNN-...md`; backend: `backend/docs/adr/NNNN-...md`, its D-table row or owning design doc; cross-cutting: owning section under `docs/`; or "none" -->

## Evidence

- [ ] Verification: <!-- `./mvnw verify` N tests green on <sha>, `pnpm check` output, or CI run link -->
- [ ] Contract: unchanged / regenerated (additive) / breaking with `contract-change-approved` requested because <!-- reason -->
- [ ] UI change: screenshot or Playwright trace attached (frontend only)
- [ ] `docs/agile/sprint-NN/log/YYYY-MM-DD.md` entry added (all PRs except `style:` and `chore(deps):`)
- [ ] `docs/quality/nfr-register.md` row added or updated (security, performance, availability, observability, cost, maintainability, CI, config, infra changes)

## Checklist

- [ ] Small PR, one concern
- [ ] No secrets committed; new environment variables documented in the area README
- [ ] Generated files not committed (`frontend/src/api/generated/`); `routeTree.gen.ts` regenerated, not hand-edited
