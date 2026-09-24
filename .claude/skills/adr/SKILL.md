---
name: adr
description: Use when recording, amending or superseding a frontend architecture decision for BlocBuy (an ADR under frontend/docs/adr), when a PR body or plan task asks for "an ADR", an "addendum", a "decision record", or when a reviewer asks where a frontend design choice is written down. Not for backend or cross-cutting decisions.
---

# Frontend ADR

## Scope check first

Only the frontend writes ADRs (ADR 0008). Before writing one, classify the decision:

| Decision is about | Record it in |
|---|---|
| Frontend stack, routing, data, build, delivery, testing, UI conventions | `frontend/docs/adr/NNNN-*.md` (this skill) |
| Backend stack, module, persistence, API shape | D-table in `backend/docs/design/backend-skeleton-plan.md` or the owning `backend/docs/design/*.md` |
| CI, deployment, quality gates, team process | dated section in the owning file under `docs/` |
| Routine or easily reversed choice | one sentence in the day's sprint log entry |

A choice earns an ADR when two or more viable options were weighed and the result is hard to
reverse or constrains later work. If in doubt, write the log sentence, not the ADR.

## Writing a new ADR

1. Number: next free `NNNN` after the last row of `frontend/docs/adr/README.md` (also `ls frontend/docs/adr`; a parallel branch may have taken the same number, renumber at rebase).
2. File: `frontend/docs/adr/NNNN-kebab-title.md` from `references/template.md`. Nygard sections only: title line `# NNNN. Title`, `Date: YYYY-MM-DD`, `## Status`, `## Context`, `## Decision`, `## Consequences`. Alternatives rejected go inside Decision as one paragraph ("Alternatives rejected: …"), each with the reason.
3. Length: under sixty lines including headings. Name files, endpoints and identifiers; do not paste code.
4. Cross-reference the ADRs it builds on by number, the plan task or issue that caused it, and the source files that implement it.
5. Status `Accepted` with the date in the README row: `| NNNN | [Title](NNNN-kebab-title.md) | Accepted (YYYY-MM-DD, <task or #issue>) |`.
6. One header comment in the main implementing file pointing at the ADR (`// Decision record: frontend/docs/adr/NNNN-….md`, as in `use-checkout.ts`). No logic change; run `pnpm fmt`.
7. Evidence: the day's sprint log entry names the ADR under `Decisions`; the PR body lists it under `Decisions`.

## Amending an existing ADR

A spike result, a review ruling or a follow-up measurement does not get a new ADR:

- Append a dated paragraph at the end of the relevant section (`## Consequences` usually), e.g. `2026-09-23 addendum (T2-08): …`. Keep the file under sixty lines; trim earlier prose before dropping facts.
- Update the README row's status text in place: `Accepted (2026-09-17, T1-15; T2-08 addendum 2026-09-23: keep the Babel preset)`.
- Never rewrite the original Decision text; readers must still see what was decided and when.

## Superseding

Write the new ADR, set the old one's `## Status` to `Superseded by NNNN (YYYY-MM-DD)`, keep the old
file, and change its README row to `Superseded by NNNN`.

## Verification before commit

```bash
wc -l frontend/docs/adr/NNNN-*.md            # < 60
grep -c "^| NNNN " frontend/docs/adr/README.md # 1
cd frontend && pnpm check                    # docs and comments must not break the gate
```

## Common mistakes

- Writing an ADR for a backend or CI decision: use the owning document instead.
- Describing the implementation instead of the decision: the Decision section says what was chosen and why, the Consequences section says what it costs and what stays open.
- Leaving the README index or the implementing file's header comment out: both are how the next reader finds the record.
- Over sixty lines: split the detail into the plan or design doc and link it.
