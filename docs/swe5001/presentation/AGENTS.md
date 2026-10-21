# SWE5001 presentation

Team 8's BlocBuy presentation for SWE5001 (Graduate Certificate in Architecting Scalable Systems), tracked in issue #461. The build pipeline, styles and shared diagrams live in [`docs/presentation-kit/`](../../presentation-kit/AGENTS.md); read that file first for commands, file shapes and design rules. This file holds what is specific to this deck. All committed text is English.

> **`src/` carries the v3 deck (since 20 October):** the content of Liu Jin's externally built v3 deck and script was ported back into `src/`, with Eric's spoken wording for slides 7–11 and the demo, so `npm run all` rebuilds `dist/` here. State of the content and the remaining corrections: [`README.md`](README.md). Hand-written speaker notes live in `notes/`.

## Facts

| | |
|---|---|
| When | Wednesday 21 October 2026, 16:15–16:55 SGT, Zoom (40 minutes including Q&A) |
| Structure | “04 Project Presentation Guidelines & Schedule” V2.8: 1 Overview, 2 Project Conduct, 3 Logical, 4 Physical, 5 DevOps, 6 Demo; segment items are copied verbatim into `src/content.js` |
| Lead | Lim Jiechao (#461); reviewer Liu Jin |
| Owners | 1–2 Lim · 3 Wang Ziang (Eric) · 4a–4c Liu Jin, 4d Yu Qianshuo (Yorik) · 5 Panpakorn Siripanich (Pan) · 6 Eric and Yorik drive, Pan for the pipeline step |
| Deck | 24 main slides, then backups BX (index) and B1a–B15; the PDF has 41 pages |
| Outputs | `dist/swe5001-presentation.html` (the deck to present; open it in Chrome or Edge), `dist/swe5001-presentation.pdf` (the same deck as a static fallback), `dist/swe5001-presentation-script.pdf` (speaker script and demo run book); all committed |

## Where things are

- `src/content.js` is the single source for speaker wording (`say`), cues, hand-overs, slot times, Q&A per segment (plus `QA_EXTENDED`, the extended bank for slides 12–15), the backup index, and the checked and open items (`CHECKED_ITEMS`, `OPEN_ITEMS`). Change wording here, never in the generated HTML.
- `src/slides-main.js` has one function per main slide; `src/slides-backup.js` builds BX and B1a–B15 (B13–B15 are simplified diagrams drawn in HTML; B11 inlines `src/assets/b11-dashboard.jpg`).
- `src/script.js` holds the script chapters: demo run book (§6), contingencies (§7), timeline (§8), open items (§9), operator commands (Appendix A) and the backup list (Appendix B), plus the coverage and briefing tables in the run sheet.
- UML diagrams (slides 9, 13, 16 and backups B3, B4, B6) are the v3 drawings in `src/diagrams/` (text drawn as glyph paths), overriding the kit's `docs/presentation-kit/diagrams/`. `src/site-geometry.js` places them in their v3 boxes.
- Motion is on (`motion: true` in `deck.config.js`): every slide carries the v3 entrance choreography in its `src/` markup. Keep it when changing a slide, and follow [`../../presentation-kit/MOTION.md`](../../presentation-kit/MOTION.md) for new elements; add any new or unique pattern there in the same commit.
- Evidence behind every number on a slide is under `docs/swe5001/evidence/`; the live-environment procedure is `docs/swe5001/runbook-environment-session.md`.

## Rules for this deck

- Never present a local result as an AWS one. Anything not run on AWS says “not run on AWS; local result: …” (slide 5 and B12).
- `{{FILL:…}}` renders as “FILL 19 Oct”: values that only exist after the 19 October evidence session. Every marker must be gone after the Tuesday 20 October rehearsal; `npm --prefix docs/swe5001/presentation run script` prints the remaining counts (0 since the v3 port).
- Speaking time is estimated at 130 words a minute. Keep each segment inside its talk budget; the run sheet shows budget vs slots vs estimate, and each card's `trim` says what to cut.
- After the talk, write every assessor remark into #461.

## Rebuild and record

From the repository root (setup and browser install: see the kit's “Setup and build”):

```sh
npm --prefix docs/swe5001/presentation run all
npm --prefix docs/swe5001/presentation run screenshots
```

Commit the source change together with the regenerated `dist/` files, and add an entry to the day's sprint log under `docs/agile/sprint-04/log/` referencing #461 (see `.claude/skills/agile-evidence-capture/SKILL.md`).
