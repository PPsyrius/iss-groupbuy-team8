---
name: swe5006-presentation-rubric
description: Draft, edit or review BlocBuy's SWE5006 final presentation (deck, speaker script, demo run book, backup videos) against docs/swe5006/presentation/RUBRIC.md. Use for any change under docs/swe5006/presentation/, for planning or timing slides, for demo or rehearsal preparation, and whenever an agent is asked to grade, score, audit or critique the presentation.
---

# SWE5006 presentation rubric

[`docs/swe5006/presentation/RUBRIC.md`](../../../docs/swe5006/presentation/RUBRIC.md) is the specification for the SWE5006 presentation, and the finished deck is graded against it by separate agents. It decides what goes in, what stays out, how long each part runs and how it looks. This skill says how to use it; it never restates it, so read the rubric itself every time. Deck mechanics (content shapes, build, markers, design rules) are in [`docs/swe5006/presentation/AGENTS.md`](../../../docs/swe5006/presentation/AGENTS.md) and [`docs/presentation-kit/AGENTS.md`](../../../docs/presentation-kit/AGENTS.md).

## Vocabulary

- **Row**: one rubric requirement, cited by its code: `G1`–`G10` (ground rules), `1.1`–`5.5` (section requirements), `V1`–`V7` (visibility), plus the time budget tables and the checklist.
- **Source** and **Ruling**: the two columns of every row. Source is what a course document requires; Ruling is the team's decision. Never cite a ruling as a document requirement, on a slide, in the script or in a review.
- **Precedence**: when two sources disagree, the rubric's precedence list decides (CA, then PDF, RT, BR, module material, team rulings). The guideline text quoted in `SEGMENTS[].items` in `src/content.js` is evidence for CA and PDF, not a higher authority than the rubric.
- **Coverage**: a row is covered by a slide, a demo step, a script line, a backup (appendix) slide, or a named report section, whichever the row's Ruling asks for.

## Drafting

1. Plan from the rubric, not from the current slides. Walk §1 to §5 in rubric order and list, for each row, what will cover it and where. Rows 1.1–5.5 each need at least one covering item; the G and V rows constrain all of them.
2. Size every section to the rubric's time budget (§1 3 min, §2 6 min, §3 3 min, §4 10 min, §5 12 min, 34 min scripted, 6 min buffer) and every §1 and §2 item to its sub-budget. A slide's `slot` must fit inside its row's minutes.
3. Record the mapping on each `SLIDES` entry as `rubric: ['1.3', 'G8']` (the kit ignores the field, so the built HTML does not change). A slide that maps to no row is superfluous: cut it, move it to a backup slide, or record the team ruling that justifies it as an open item.
4. Follow each row's Ruling for form: one example on the slide and the report section named for the rest (G8), one idea per slide (V1), the specific diagrams the row names (for example the team-formation statechart in 2.4, before/after diagrams in 2.5).
5. Use the course vocabulary the rubric cites (EPAT DoD ladder, Agile Testing Quadrants, Continuous Delivery versus Deployment, GoF class-level patterns only). Wording that contradicts a row's Ruling is a defect even if it is true.
6. Facts still come from merged code, issues, the board, AWS and recorded evidence (see the deck's AGENTS.md). If a row needs a fact that does not exist yet, use `{{FILL:…}}`; never fill a row with a guess to make it look covered.
7. Each open decision at the end of the rubric (1–9) holds back the rows that depend on it until it is settled: for example, decision 7 (performance tool and result) decides what 3.2 and 5.3 can show, and decision 8 (auto-scaling) decides whether 5.2 demonstrates scalability or states it as a trade-off. Never settle an open decision yourself: ask the person, or leave a `{{CONFIRM:…}}` marker on each affected slide and an `OPEN_ITEMS` row naming the decision number.

## Editing

- Before changing a slide, read its `rubric` rows and the rows' Rulings. An edit that drops coverage of a row, pushes a section past its budget or breaks a V rule needs a replacement elsewhere in the same change.
- Content that serves no row is the first thing to cut when a section runs over (reason 1 of the rubric: no superfluous or overemphasised content). Time spent on a row should be in proportion to its sub-budget, not to how much material exists.
- When the rubric itself changes, re-plan the affected sections from the new rows rather than patching slides around the old ones, and update every `rubric` field that cites a renumbered row.
- After any edit, rebuild and run the screenshots step (the kit's commands), then re-check the touched rows with the review procedure below.

## Reviewing

Use this when asked to review, grade or audit the presentation, and as the self-check before handing a draft back. A grading agent should be independent: it reads the rubric, the deck sources and the built outputs, not the drafter's conversation.

Inputs, in this order: `RUBRIC.md`; `src/content.js` (`SEGMENTS`, `SLIDES` with `rubric`, `QA`, `BACKUPS`, `OPEN_ITEMS`); `src/slides-main.js` and `src/slides-backup.js`; `src/script.js`; the built `dist/swe5006-presentation.html` (and `png/` screenshots after `npm --prefix docs/swe5006/presentation run screenshots`); the script build's run sheet and marker counts. Course documents named in the rubric's Sources table (CA, PDF, RT, BR, PROP, module material) are kept out of the repository; judge against the rubric's Source column when they are not available, and say so.

Procedure:

1. **Coverage.** For every row G1–G10, 1.1–5.5 and V1–V7, find what covers it. Do not trust the `rubric` field alone: read the slide, `say` and cues and confirm they meet the Ruling's specific asks (for example 1.3 needs the promised, delivered, changed and dropped columns with ADRs; 2.2 needs object types affected and a before/after pair).
2. **Superfluity and emphasis.** List every slide, cue and script chapter that maps to no row, and every row whose share of speaking time is clearly out of line with its sub-budget.
3. **Timing.** Compare each section's slot total and estimated speaking time (130 words a minute) with the rubric's budget; the script run sheet prints both.
4. **Source fidelity.** Flag any slide or script line that presents a team ruling as a course requirement, cites the wrong source, or contradicts a Ruling.
5. **Visibility.** Check V1–V7 on the screenshots: label size, contrast, crop and highlight, schema scope, overflow lines from the screenshots step.
6. **Checklist and open decisions.** Report each checklist box and open decision as done, not done or not verifiable from the repository.

Verdicts per row: **Met** (Ruling satisfied with evidence), **Partial** (covered, but a named part of the Ruling is missing), **Missing** (nothing covers it), **Conflict** (content contradicts the Ruling or the Source), **N/A** (the row is satisfied outside the deck, such as G9's scribe; say where). Every verdict other than Met cites the row, the slide number or file location, and the smallest fix.

Report shape:

```markdown
## Summary
Met N · Partial N · Missing N · Conflict N · N/A N. Scripted time M:SS of 34:00. Superfluous items N.

## Rows
| Row | Verdict | Where | Finding and fix |
|---|---|---|---|

## Superfluous or overemphasised
| Slide / location | Maps to | Problem | Fix |
|---|---|---|---|

## Timing
| Section | Budget | Slots | Estimated speech | Verdict |
|---|---|---|---|---|

## Checklist and open decisions
| Item | Status | Evidence |
|---|---|---|
```

Order findings by grading impact: Missing and Conflict on 1.1–5.5 first, then timing overruns, then Partial, then V rows and superfluity. A review is read-only; it changes no files unless the person asks for fixes.
