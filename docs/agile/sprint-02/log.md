# Sprint 2 log (2026-09-14 to 2026-09-25)

Goal: see the `[T1] Sprint 2` container issue once filed (`frontend/docs/plan.md` T1-02). Tier 1
targets the week ending 2026-09-20, tier 2 the week ending 2026-09-25; code freeze 2026-09-24 EOD.

One entry per unit of work per member, newest at the bottom. Shape and rules:
`.claude/skills/agile-evidence-capture/references/templates.md`. Three to eight lines each.
Say what was verified and what was not.

## 2026-09-16 - @limjiechao - Evidence conventions and agile-evidence-capture skill

- Kind: ops, decision
- Changed: `.claude/skills/agile-evidence-capture/`, `.claude/settings.json` (commit hook),
  `docs/agile/`, `docs/quality/nfr-register.md`, `.github/ISSUE_TEMPLATE/`,
  `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`,
  `frontend/docs/plan.md` (sprint log moved repo-wide, skill renamed)
- Verified: hook script exercised with sample commits (NFR config without evidence -> checklist;
  `style:` and `Evidence-skipped:` -> silent; enforce mode -> exit 2); snapshot script run against
  the live board into a scratch directory. Skill evaluated on four task prompts (login rate
  limiting, pin actions by SHA, UC1 `savingsPercent`, sprint close) in throwaway clones, each run
  with and without the skill and graded against 27 assertions: 27/27 with, 15/27 without; code
  quality equal in both, the difference is entirely the evidence artefacts. Not yet exercised by a
  teammate's session.
- Decisions: ADR `frontend/docs/adr/0008-documentation-and-evidence.md` (Accepted): sprint log and
  NFR register are repo-wide; ADRs stay a frontend practice, the backend keeps its D-table and
  design docs, cross-cutting decisions live in the owning `docs/` page
- Problems: Sprint 1 report shipped with TODO burndown and contribution cells; NFR changes
  (#84, #75, #45) had no issue or doc trail. Action: this change set. Status: in review.
- Next: file the Sprint 2 container issue (T1-02), create `nfr:*` labels, first board snapshot.
