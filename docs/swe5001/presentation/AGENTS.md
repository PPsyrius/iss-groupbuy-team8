# SWE5001 presentation

Team 8's BlocBuy presentation for SWE5001 (Graduate Certificate in Architecting Scalable Systems), tracked in issue #461. The build pipeline, styles and shared diagrams live in [`docs/presentation-kit/`](../../presentation-kit/AGENTS.md); read that file first for commands, file shapes and design rules. This file holds what is specific to this deck. All committed text is English.

## Facts

| | |
|---|---|
| When | Wednesday 21 October 2026, 16:15–16:55 SGT, Zoom (40 minutes including Q&A) |
| Structure | “04 Project Presentation Guidelines & Schedule” V2.8: 1 Overview, 2 Project Conduct, 3 Logical, 4 Physical, 5 DevOps, 6 Demo; segment items are copied verbatim into `src/content.js` |
| Lead | Lim Jiechao (#461); reviewer Liu Jin |
| Owners | 1–2 Lim · 3 Wang Ziang (Eric) · 4a–4c Liu Jin, 4d Yu Qianshuo (Yorik) · 5 Panpakorn Siripanich (Pan) · 6 Eric and Yorik drive, Pan for the pipeline step |
| Deck | 24 main slides, then backups BX (index) and B1–B15; the PDF has 41 pages |
| Outputs | `dist/swe5001-presentation.{html,pdf}`, `dist/swe5001-presentation-script.{html,pdf}` (committed) |

## Where things are

- `src/content.js` is the single source for speaker wording (`say`), cues, hand-overs, slot times, Q&A per segment, the backup index and the open items. Change wording here, never in the generated HTML.
- `src/slides-main.js` has one function per main slide; `src/slides-backup.js` builds BX and B1–B15 (B13–B15 are simplified diagrams drawn in HTML).
- `src/script.js` holds the script chapters: demo run book (§6), contingencies (§7), timeline (§8), open items (§9), operator commands (Appendix A) and the backup list (Appendix B), plus the coverage and briefing tables in the run sheet.
- UML diagrams (slides 9, 13, 16 and backups B3, B4, B6) come from `docs/presentation-kit/diagrams/`. Copy a diagram into `src/diagrams/` before changing it for this deck only.
- Evidence behind every number on a slide is under `docs/swe5001/evidence/`; the live-environment procedure is `docs/swe5001/runbook-environment-session.md`.

## Rules for this deck

- Never present a local result as an AWS one. Anything not run on AWS says “not run on AWS; local result: …” (slide 5 and B12).
- `{{FILL:…}}` renders as “FILL 19 Oct”: values that only exist after the 19 October evidence session. Every marker must be gone after the Tuesday 20 October rehearsal; `npm run build:script` prints the remaining counts.
- Speaking time is estimated at 130 words a minute. Keep each segment inside its talk budget; the run sheet shows budget vs slots vs estimate, and each card's `trim` says what to cut.
- After the talk, write every assessor remark into #461.

## Rebuild and record

From the repository root (setup and browser install: see the kit's “Setup and build”):

```sh
npm --prefix docs/swe5001/presentation run all
npm --prefix docs/swe5001/presentation run screenshots
```

Commit the source change together with the regenerated `dist/` files, and add an entry to the day's sprint log under `docs/agile/sprint-04/log/` referencing #461 (see `.claude/skills/agile-evidence-capture/SKILL.md`).
