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
| Contributors | Each report section owner supplies the matching slides (use cases and design problems, architecture, DevSecOps); one speaker per section (rubric G4); drafts in `docs/swe5006/report-drafts/` and #463 |
| Deadlines | backup videos for §4 and §5 recorded by 24 Oct (rubric G6); every rubric checklist box ticked and open decision settled by 25 Oct; timed rehearsal 25 Oct |
| Demo storyline (#464) | invite → START/JOIN with Stripe Sandbox → `/account` orders and blocs → expiry refund, live on deployed `dev`, with a fallback recording |
| Outputs | `dist/swe5006-presentation.{html,pdf}` (PDF for submission, HTML for presenting), `dist/swe5006-presentation-script.pdf` (all committed; the script has no HTML output) |

## Current state: draft predating the rubric

The deck was drafted in #566 (22 main slides, backups, script) from the lecturer's guideline before `RUBRIC.md` existed. `SLIDES` entries carry guideline item codes (`item: '1c'`) but no `rubric` rows yet, and the draft departs from the rubric in at least these places:

- Timing: `SEGMENTS` budgets §4 11 min and §5 14 min plus 8 min Q&A (37 min of talk, ending 17:15); the rubric scripts 34 min (§4 10, §5 12) with a 6 min buffer inside a 40 min hard stop.
- §1 order and coverage: the draft runs DoD (1.6) before milestones (1.4) and folds retrospectives (1.7) into the status slide; the rubric also asks for an ownership matrix (1.5) and a promised-versus-delivered table against the proposal (1.3).
- §2: the draft gives each of five design problems its own slide and puts the transition rule on the architecture slide and backup B7; the rubric asks for a strategy table with a before/after pair (2.2), one use case taken from analysis to design with the team-formation statechart (2.4, not in the draft), and one design problem in full before/after form with the rest listed (2.5).
- §3 and §5: the k6 performance baseline appears only as a testing-table row and a Q&A answer; the rubric introduces the scripted test in §3 (3.2) and runs it in §5 (5.3) against 2 s at 100 concurrent users.

Re-plan from the rubric (skill, Drafting) rather than patching these one by one; `OPEN_ITEMS` in `src/content.js` and the rubric's open decisions 1–9 list what still needs a person's answer.

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
