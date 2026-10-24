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

The deck was re-planned from `RUBRIC.md` on 21–22 Oct 2026 (#464) and replaces the #566 draft; the round-4 review (22 Oct) reworked it for density: 39 main slides, backups B1–B16 and the script, with segments at the rubric's budgets (§1 3, §2 6, §3 3, §4 10, §5 12 min; 34 min scripted, 6 min buffer). Every `SLIDES` entry records its `rubric` rows. §2 follows 2.1 → 2.6 with UML diagrams in `src/diagrams/` (package view, transition pair, use cases, UC4 analysis and design models, team statechart, one 20 s GoF problem per member, State in full before/after form, ERD); a diagram sits in a pane that fills its box and scrolls along one axis in presenter mode (scaled to fit in the PDF and screenshots), with each crop outlined, and is then stepped through 1:1 crops, each with a short caption; the PDF carries every crop with its caption on a detail page after the backups (D9-1 … D31-3, indexed by DX, generated from the layers each slide declares, including slide 25's four quadrant details), and every unique diagram also has a full-size backup (F1 … F21, indexed by FX) that scrolls both ways at 1:1 while presenting. The report line and, in the PDF only, the detail-page note sit in the header beside the speaker chip, so this deck raises the body to 108 px (568 px tall; `BODY_TOP`, `BODY_H` in `src/slides-main.js`).

Still open, all needing the team rather than the repository (see `OPEN_ITEMS` in `src/content.js` and the rubric's checklist):

- The Sprint 4 retrospective (1.6, 1.7; Sprint 3's is `docs/agile/sprint-03/retro.md`). Use-case owners were settled on 24 Oct (open decision 4).
- The paid flow on dev: the day-of go/no-go and closing #590, #517 and #223 (paid START and the expiry refund were shown by PR #664, merged 24 Oct, bef39b72; a second buyer's paid JOIN on 24 Oct in the system demo backup recording, #657), and the pipeline backup video by 25 Oct (G6, 4.6).
- ADRs for the six changes on slide 4 that have none (listed in its `data-ref`), the demo change and its approver (4.1).

## Working on the deck

1. Plan the slides per rubric section with the lead; who presents each section follows rubric G4 (open decision 9 is settled: the talk is graded as a team), so ask or check #464 rather than copying SWE5001's owners. Each `SLIDES` entry has speaker, item, `rubric` rows, slot in seconds, title, `say` and cues, and a matching function in `src/slides-main.js`, following the kit's “Adding, removing or moving a slide” (numbering, `darkSlides`, segment `talk` and `start`). `docs/swe5001/presentation/src/slides-main.js` has a worked example of every layout pattern.
2. Keep `SEGMENTS[].items` as the guideline wording verbatim (it renders in each segment's requirement box) and the `talk` minutes equal to the rubric's time budget.
3. Reuse diagrams before drawing new ones: `logical-view`, `physical-deployment`, `context-map`, `aggregates`, `expiry-refund-sequence`, `uc4-join-sequence`. New or changed diagrams follow [`docs/presentation-kit/diagrams/STYLE.md`](../../presentation-kit/diagrams/STYLE.md) and the rubric's V1–V7; copy a shared diagram into `src/diagrams/` before changing it for SWE5006 only.
4. Write the script chapters in `src/script.js` (demo step cards, contingencies, timeline), following `docs/swe5001/presentation/src/script.js`.
5. Mark anything unknown with `{{FILL:…}}`, `{{VERIFY:…}}` or `{{CONFIRM:…}}`; all must be gone after the 25 Oct rehearsal. Replace `scriptHeader.right` with the version date.

## Rules for this deck

- Facts on slides come from the report drafts, merged code, GitHub issues and the project board, AWS (read-only) or recorded evidence; never present a local result as one from a deployed environment.
- Speaking time is estimated at 130 words a minute; keep each section inside the rubric's budget (the run sheet compares them).
- Motion is on (`motion: true`): slides crossfade, and every main slide, backup, index and full-size page has entrances from the patterns in [`docs/presentation-kit/MOTION.md`](../../presentation-kit/MOTION.md); the paged 1:1 windows (B16-n, the D pages) have none on purpose. Give new or changed elements an entrance from those patterns, and add any new or unique pattern there in the same commit. The helpers in `src/` take motion as an optional argument (`tbl(m)`, `dt(m)`, `row`, `lines`, `kv`, the pane view opts `d`, `od`, `os`, `pa`, `fx`), so leaving it out keeps the markup unchanged; never put `.a` on a crop layer, badge, caption or anything in `CROPS`, because the D pages reuse that markup. Check a change with a settled-frame pixel diff of `run screenshots` before and after (it must match) plus frames at 300 and 900 ms in presenter mode.
- Density (review round 4): one point per line, never "A · B · C" runs; prefer sparse slides at 20 px or more over dense ones; detail goes into crop captions, the speaker notes or the backups, not text blocks beside a diagram.
- Record references that help the report but not the talk (issue, PR and ADR numbers, file names, course page references) stay off the slide face: put them in a `data-ref` attribute on the element they support (`ref()` and `withRef()` in `src/slides-main.js`), or in the slide's cues. List them with `grep -o 'data-ref="[^"]*"' dist/swe5006-presentation.html`.
- Number every fragment step in `say` as `[click k/n]`; the deck build fails when a slide's steps and its notes disagree.
- Do not change the SWE5001 deck (#461) from here. A kit change must leave the SWE5001 HTML unchanged unless that change is intended (see the kit's “Changing the kit”).

## Rebuild and record

From the repository root (setup and browser install: see the kit's “Setup and build”):

```sh
npm --prefix docs/swe5006/presentation run all
npm --prefix docs/swe5006/presentation run screenshots
```

Commit the source change together with the regenerated `dist/` files, and add an entry to the day's sprint log under `docs/agile/sprint-04/log/` referencing #464 (see `.claude/skills/agile-evidence-capture/SKILL.md`). Before handing a draft back, run the skill's review procedure and fix every Missing and Conflict row or record it as an open item.
