# SWE5006 presentation

Team 8's BlocBuy final presentation and demo for SWE5006, tracked in issue #464. It must look, build and ship exactly like the SWE5001 deck: the pipeline, styles and shared UML diagrams live in [`docs/presentation-kit/`](../../presentation-kit/AGENTS.md), so read that file first for commands, file shapes and design rules. All committed text is English.

## Facts

| | |
|---|---|
| When | Monday 26 October 2026, 16:30–17:15 SGT, Zoom (link in #464) |
| Weight | 20% of the project mark (the report is 80%), so slides reuse report material rather than new content |
| Lead | Yu Qianshuo (Yorik), storyline and demo script; slide master Lim Jiechao |
| Contributors | Each report section owner supplies the matching slides (use cases and design problems, architecture, DevSecOps); drafts in `docs/swe5006/report-drafts/` and #463 |
| Deadlines | slides draft 23 Oct; fallback recording on the deployed `dev` environment by 25 Oct; timed rehearsal 25 Oct |
| Demo storyline (#464) | invite → START/JOIN with Stripe Sandbox → `/account` orders and blocs → expiry refund, live on deployed `dev`, with a fallback recording |
| Outputs | `dist/swe5006-presentation.{html,pdf}` (PDF for submission, HTML for presenting), `dist/swe5006-presentation-script.pdf` (all committed; the script has no HTML output) |

## Current state: scaffold

The deck builds end to end but holds placeholders: two segments with placeholder guideline items, three slides (title, the shared logical view, thank-you), backup BX and B1 (the shared context map), and a draft demo table. `deck.config.js` sets `demoSegment: null` and a scaffold PDF header.

## Start here

1. Get the SWE5006 presentation guideline (ask the person; it is not in the repository). Copy its segments, timings and item wording verbatim into `SEGMENTS` in `src/content.js` (`start` times from 16:30:00), and set `guidelineLabel` in `deck.config.js` to name the guideline and its version.
2. Plan the slides per segment with the lead; who presents each section is not recorded yet, so ask or check #464 rather than copying SWE5001's owners. Add each slide to `SLIDES` (speaker, item, slot in seconds, title, `say`, cues) and a matching function in `src/slides-main.js`, following the kit's “Adding, removing or moving a slide” (numbering, `darkSlides`, segment `talk` and `start`). `docs/swe5001/presentation/src/slides-main.js` has a worked example of every layout pattern.
3. Reuse diagrams before drawing new ones: `logical-view`, `physical-deployment`, `context-map`, `aggregates`, `expiry-refund-sequence`, `uc4-join-sequence`. New or changed diagrams follow [`docs/presentation-kit/diagrams/STYLE.md`](../../presentation-kit/diagrams/STYLE.md); copy a shared diagram into `src/diagrams/` before changing it for SWE5006 only.
4. Write the script chapters in `src/script.js` (demo step cards, contingencies, timeline), following `docs/swe5001/presentation/src/script.js`; set `demoSegment` once the demo has its own segment.
5. Mark anything unknown with `{{FILL:…}}`, `{{VERIFY:…}}` or `{{CONFIRM:…}}`; all must be gone after the 25 Oct rehearsal. Replace the scaffold `scriptHeader.right` with the version date.

## Rules for this deck

- Facts on slides come from the report drafts, merged code or recorded evidence; never present a local result as one from a deployed environment.
- Speaking time is estimated at 130 words a minute; keep each segment inside its budget (the run sheet compares them).
- Do not change the SWE5001 deck (#461) from here. A kit change must leave the SWE5001 HTML unchanged unless that change is intended (see the kit's “Changing the kit”).

## Rebuild and record

From the repository root (setup and browser install: see the kit's “Setup and build”):

```sh
npm --prefix docs/swe5006/presentation run all
npm --prefix docs/swe5006/presentation run screenshots
```

Commit the source change together with the regenerated `dist/` files, and add an entry to the day's sprint log under `docs/agile/sprint-04/log/` referencing #464 (see `.claude/skills/agile-evidence-capture/SKILL.md`).
