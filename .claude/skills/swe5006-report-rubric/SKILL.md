---
name: swe5006-report-rubric
description: Draft, edit or review BlocBuy's SWE5006 final report against docs/swe5006/report/RUBRIC.md. Use for any change under docs/swe5006/report/ or docs/swe5006/report-drafts/, for hydrating a report section from its plan issue (#675–#681), for incorporating the presentation remarks, for the freeze and submission checks (#695), and whenever an agent is asked to grade, score, audit or critique the report.
---

# SWE5006 report rubric

[`docs/swe5006/report/RUBRIC.md`](../../../docs/swe5006/report/RUBRIC.md) is the specification for the SWE5006 final report, and the finished report is graded against it by separate agents. It decides what each template section must contain, which team rulings apply and which claims are easy to get wrong. This skill says how to use it; it never restates it, so read the rubric itself every time. File layout, markers and commands are in [`docs/swe5006/report/AGENTS.md`](../../../docs/swe5006/report/AGENTS.md). What to write in each section, bullet by bullet with sources, is in the plan: the index comment on #463 and the chapter issues #675–#681.

## Vocabulary

- **Row**: one rubric requirement, cited by its code: `G1`–`G12` (ground rules), `F` and `1.1`–`5` (sections), `A`–`E` (appendices), `3.4-A`…`3.4-E` and `3.5-1`…`3.5-6` (member checklists), `F1`–`F8` (figures), `REM-nn` (presentation remarks), `D#` (open decisions).
- **Source** and **Ruling**: Source is what a course document (TPL, BRF, REM) requires; Ruling is the team's decision. Never present a ruling as a course requirement in the report or in a review.
- **Plan bullet**: one item in #675–#681, ending in `— source:`. A `GAP` bullet names something missing and its owner.
- **Freeze SHA**: the one `dev` commit every number is re-read at (G7).

## Drafting a section

1. Open the section file in `docs/swe5006/report/sections/`, read its header (`rubric`, `owner`, `plan`, `sources`) and the rubric rows it cites.
2. Read the section's plan issue (`gh issue view <n>`) and the ready drafts it points to in `docs/swe5006/report-drafts/`. Carry over ready drafts; do not rewrite sound text.
3. Write answer first, in the template's order of asks. Every rubric Ruling for the row is met or carries a marker.
4. Check every fact at its source before writing it, and every claim against the rubric's "easy to get wrong" table. State the evidence kind. A fact you cannot verify becomes `{{VERIFY: …}}`; a missing one `{{FILL: …}}`; one waiting on an open decision `{{CONFIRM: D# …}}`. Never fill a row with a guess to make it look covered.
5. Never settle an open decision (rubric D#) yourself; leave the marker and name the owner.
6. Class, method, table and event names: confirm each in the code (`rg`), at the freeze SHA once it exists.
7. Figures: reuse deck diagrams by path; draw new UML as Mermaid fences (AGENTS.md lists the supported subset), each followed by its caption, and look at them with `npm --prefix docs/swe5006/report run figure -- <section file>`. They follow F1–F8.
8. Run `npm --prefix docs/swe5006/report run check`, then the review procedure below on the section; fix every Missing and Conflict.
9. Set the header `status:` to `draft` or `ready`; `ready` means no marker left in the file and a clean review.

## Editing

- Before changing a section, read its rows. An edit that drops a Ruling's ask, breaks a member checklist part or contradicts another section needs a fix in the same change.
- One fact, one place: when a number changes, search the other sections for it (`rg -n "2,048" docs/swe5006/report/sections`).
- When the rubric changes, re-check every section that cites the changed rows.

## Incorporating presentation remarks (G10)

1. Read the REM issue (presentation rubric G9). Copy each remark verbatim into the rubric's Remarks register with a section number.
2. For each, either change the named section or record "not adopted" with the reason. Status is done only when the report text changed or the reason is written.
3. Comment on the plan issue of the affected chapter, as #675–#681 ask.

## Reviewing

Use this when asked to review, grade or audit the report, and as the self-check before handing a section back. A grading agent should be independent: it reads the rubric, the sections and the cited sources, not the drafter's conversation. Course documents (TPL, BRF, PROP) are kept out of the repository; judge against the rubric's Source column when they are not available, and say so.

Procedure:

1. **Structure.** Run the check script; its errors are Conflicts on G2, G3 or the checklists.
2. **Coverage.** For every row in scope, read the text and confirm it meets the Ruling's specific asks (for example 2.2 needs promised, delivered, changed and dropped with ADRs; 3.2.N needs all four parts and both rationale lines; 3.4.N needs A–E with one design sequence per analysis sequence; 3.5.N needs before diagrams and a liability).
3. **Ownership.** Each member heads one 3.4.N and one 3.5.N; chapter 5 credits match the sections (G4).
4. **Evidence fidelity.** Sample at least five facts per chapter and trace each to its source. Flag upgraded results (local written as dev, SWE5001 as SWE5006, estimate as measurement), contradictions of the "easy to get wrong" table, numbers that differ between sections, and rulings presented as course requirements.
5. **Figures.** F1–F8 on each figure: notation, caption, legibility at A4, names that exist in the code.
6. **Remarks and decisions.** Each REM row and each open decision: done, not done or not verifiable from the repository.

Verdicts per row: **Met**, **Partial** (a named part of the Ruling is missing), **Missing**, **Conflict** (contradicts the Ruling, the Source or the evidence), **N/A** (say why). Every verdict other than Met cites the row, the file and line, and the smallest fix.

Report shape:

```markdown
## Summary
Met N · Partial N · Missing N · Conflict N · N/A N. Open markers N. Check script: pass/fail.

## Rows
| Row | Verdict | Where | Finding and fix |
|---|---|---|---|

## Evidence sampled
| Claim | Section | Source checked | Result |
|---|---|---|---|

## Remarks and open decisions
| Item | Status | Evidence |
|---|---|---|
```

Order findings by grading impact: Missing and Conflict on 3.2–3.5 (the per-member, most heavily weighted content) first, then chapters 1, 2 and 4, then Partial, then figures and wording. A review is read-only; it changes no files unless the person asks for fixes.

## Freeze and submission (G1, G7)

1. 30 Oct: record the freeze SHA in #695. Re-read every count, version, coverage figure and status at it; replace each with its dated value.
2. `npm --prefix docs/swe5006/report run check:strict` passes (no markers).
3. Build the PDF (`npm --prefix docs/swe5006/report run build`), then read every page: figure legibility, page breaks, contents, no red marker boxes.
4. Run the review procedure on the whole report; zero Missing and Conflict.
5. Post the submitter and time on #695.
