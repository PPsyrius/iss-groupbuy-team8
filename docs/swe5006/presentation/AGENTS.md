# SWE5006 presentation

Team 8's BlocBuy final presentation and demo for SWE5006, tracked in issue #464. It must look, build and ship exactly like the SWE5001 deck: the pipeline, styles and shared UML diagrams live in [`docs/presentation-kit/`](../../presentation-kit/AGENTS.md), so read that file first for commands, file shapes and design rules. All committed text is English.

## The rubric governs the content

[`RUBRIC.md`](RUBRIC.md) is the specification for this presentation. It sets what each section must cover, what to leave out, the time budget, the speakers and the visibility rules, and the finished deck is graded against it by separate agents. Read it before drafting, editing or reviewing anything here, and follow the [`swe5006-presentation-rubric`](../../../.claude/skills/swe5006-presentation-rubric/SKILL.md) skill for the procedure:

- **Draft** from the rubric's rows in order (§1 → §5, then G and V rules), not from the existing slides. Every slide serves at least one row and records it as `rubric: ['1.3']` on its `SLIDES` entry; a slide that serves none is cut or moved to a backup.
- **Edit** without dropping a row's coverage, exceeding its time budget or breaking a V rule.
- **Review** row by row (Met, Partial, Missing, Conflict, N/A), plus superfluous content, timing and the rubric's checklist, in the report shape the skill defines.

When the rubric and anything else in this folder disagree (this file, `SEGMENTS`, `OPEN_ITEMS`, the script), the rubric wins; fix the other file in the same change. Rulings are the team's own decisions; never present one on a slide as a course requirement.

## Facts

| | |
|---|---|
| When | Monday 26 October 2026, 16:30 SGT, Zoom (link in #464); 40 min hard stop including interruptions (rubric G1) |
| Weight | 20% of the project mark (the report is 80%), so slides reuse report material rather than new content |
| Lead | Yu Qianshuo (Yorik), storyline and demo script; slide master Lim Jiechao |
| Contributors | Each report section owner supplies the matching slides (use cases and design problems, architecture, DevSecOps); one speaker per section except §2, where each member presents their own design problem (rubric G4, seven handovers); drafts in `docs/swe5006/report-drafts/` and #463 |
| Deadlines | backup videos for §4 and §5 recorded by 24 Oct (rubric G6); every rubric checklist box ticked and open decision settled by 25 Oct; timed rehearsal 25 Oct |
| Demo storyline (#464) | invite → START/JOIN with Stripe Sandbox → `/account` orders and blocs → expiry refund, live on deployed `dev`, with a fallback recording |
| Outputs | `dist/swe5006-presentation.{html,pdf}` (PDF for submission, HTML for presenting), `dist/swe5006-presentation-script.pdf` (all committed; the script has no HTML output) |

## Current state: rubric-based draft

The deck was re-planned from `RUBRIC.md` on 21–22 Oct 2026 (#464) and replaces the #566 draft: 37 main slides, backups B1–B16 and the script, with segments at the rubric's budgets (§1 3, §2 6, §3 3, §4 10, §5 12 min; 34 min scripted, 6 min buffer). Every `SLIDES` entry records its `rubric` rows. §2 follows 2.1 → 2.6 with UML diagrams in `src/diagrams/` (package view, transition pair, use cases, UC4 analysis and design models, team statechart, one 20 s GoF problem per member, State in full before/after form, ERD); large diagrams are shown scaled for orientation, with each crop outlined, and stepped through 1:1 crops in presenter mode; the PDF carries every crop 1:1 on a detail page after the backups (D9-1 … D29-3, generated from the crops each slide declares).

Still open, all needing the team rather than the repository (see `OPEN_ITEMS` in `src/content.js` and the rubric's checklist):

- Actual effort per member (1.4), owners of UC1, UC2, UC5 and UC7 (1.5, open decision 4), the Sprint 3 and Sprint 4 retrospectives and the Sprint 4 DoD step (1.6, 1.7).
- The 23 Oct go/no-go for the paid flow on dev (#517, #223), the k6 pre-run on the SWE5001 ECS environment (5.3), and both backup videos by 24 Oct (G6, 4.6, 5.5).
- Report section numbers (#463), ADRs for the changes marked "no ADR" on slide 4, the scribe (G9), the demo change and its approver (4.1), Identity Center MFA (4.2).

## Working on the deck

1. Plan the slides per rubric section with the lead; who presents each section follows rubric G4 and open decision 9, so ask or check #464 rather than copying SWE5001's owners. Each `SLIDES` entry has speaker, item, `rubric` rows, slot in seconds, title, `say` and cues, and a matching function in `src/slides-main.js`, following the kit's “Adding, removing or moving a slide” (numbering, `darkSlides`, segment `talk` and `start`). `docs/swe5001/presentation/src/slides-main.js` has a worked example of every layout pattern.
2. Keep `SEGMENTS[].items` as the guideline wording verbatim (it renders in each segment's requirement box) and the `talk` minutes equal to the rubric's time budget.
3. Reuse diagrams before drawing new ones: `logical-view`, `physical-deployment`, `context-map`, `aggregates`, `expiry-refund-sequence`, `uc4-join-sequence`. New or changed diagrams follow [`docs/presentation-kit/diagrams/STYLE.md`](../../presentation-kit/diagrams/STYLE.md) and the rubric's V1–V7; copy a shared diagram into `src/diagrams/` before changing it for SWE5006 only.
4. Write the script chapters in `src/script.js` (demo step cards, contingencies, timeline), following `docs/swe5001/presentation/src/script.js`.
5. Mark anything unknown with `{{FILL:…}}`, `{{VERIFY:…}}` or `{{CONFIRM:…}}`; all must be gone after the 25 Oct rehearsal. Replace `scriptHeader.right` with the version date.

## Rules for this deck

- Facts on slides come from the report drafts, merged code, GitHub issues and the project board, AWS (read-only) or recorded evidence; never present a local result as one from a deployed environment.
- Speaking time is estimated at 130 words a minute; keep each section inside the rubric's budget (the run sheet compares them).
- Motion is on (`motion: true`): every slide carries entrance choreography in its `src/` markup. Give new or changed elements an entrance from the patterns in [`docs/presentation-kit/MOTION.md`](../../presentation-kit/MOTION.md), and add any new or unique pattern there in the same commit.
- Do not change the SWE5001 deck (#461) from here. A kit change must leave the SWE5001 HTML unchanged unless that change is intended (see the kit's “Changing the kit”).

## Rebuild and record

From the repository root (setup and browser install: see the kit's “Setup and build”):

```sh
npm --prefix docs/swe5006/presentation run all
npm --prefix docs/swe5006/presentation run screenshots
```

Commit the source change together with the regenerated `dist/` files, and add an entry to the day's sprint log under `docs/agile/sprint-04/log/` referencing #464 (see `.claude/skills/agile-evidence-capture/SKILL.md`). Before handing a draft back, run the skill's review procedure and fix every Missing and Conflict row or record it as an open item.
