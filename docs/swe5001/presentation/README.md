# SWE5001 presentation

Team 8's deck and speaker script for the SWE5001 final presentation on Wednesday 21 October 2026, 16:15–16:55 SGT (Zoom). Tracked in #461. `dist/` holds the version to present (v3), built from `src/` with `npm run all` (see [`AGENTS.md`](AGENTS.md)); `notes/` holds hand-written speaker notes.

| File | What it is |
| --- | --- |
| `dist/swe5001-presentation.html` | The deck to present: 24 main slides and 17 backups (BX, B1a–B15), self-contained. Open it in Chrome or Edge. Keys: → / Space next, ← back, S speaker notes with the timer (T starts it), B backup index, O overview, F fullscreen. |
| `dist/swe5001-presentation.pdf` | The same deck as a 41-page PDF, printed with Playwright. A static fallback if the HTML cannot be shared, and the readable copy on GitHub. |
| `dist/swe5001-presentation-script.pdf` | Speaker script and demo run book (39 pages): run sheet, one card per slide with wording and timing, the demo steps, contingencies, timeline, open items, operator commands and the backup list. Slides 7–11 and the demo use Eric's spoken wording. |
| [`notes/speaker-script-eric.md`](notes/speaker-script-eric.md) | Eric's segment 3 (slides 7–11) and demo (slide 23, steps 1–4) reworded for speaking, now carried in the script PDF. Its step 2 still describes the earlier one-task k6 plan; the script follows the 20 October demo. |
| [`notes/qa-eric.md`](notes/qa-eric.md) | Eric's Q&A prep for segment 3 and the demo: 26 likely questions with 30–45 s answers, the backup slide to open and the source of each answer. |

## State of the content (20 October)

- Results are final. V3 HTTP auto-scaling ran on AWS on 19 October: commerce scaled 1 → 3 tasks on its own and served about 455 requests per second at server p95 under 50 ms. The load generator hit its limit first, so it is not V3 acceptance ([evidence](../evidence/20261019-v3-autoscaling/README.md)).
- Queue scaling, self-healing, the poison message, the identity outage and the buyer journey end to end did not run on AWS. Slide 5 and backup B12 say "not run on AWS", with the local result where one exists ([15 October local rehearsal](../evidence/20261015-local-failure-rehearsal/README.md)).
- The live demo has three steps and a close: use cases, auto-scaling, and the promotion pipeline. Stopping a task, the identity outage and the event backlog were cut because they were not rehearsed on AWS; slide 23 and step 4 say the identity outage and the bad message were tested locally and stopping a server was not run.
- No load test runs during the talk. Demo step 2 shows the live CloudWatch dashboard (commerce at its standing 2 tasks), then the graphs of the 19 October run on AWS; the slide 23 footer says so. A live V3 run would need the commerce floor lowered to 1 and a fresh preflight minutes before the load (k6 validates the snapshot in `perf/k6/lib.mjs`; `infra/swe5001/scripts/http-preflight.sh` only captures it), which the team judged too much to operate on air.
- The effort split on slide 6 (12.5 / 11.5 / 9 / 8.5 / 8.5 person-days) is agreed by all five members. The deck has no FILL, VERIFY or CONFIRM markers left.
- Still open: fallback recordings for demo steps 1 and 3, the slide 16 choice (UML sequence or backup B15), and alarm e-mail confirmation (#515).

## How these files are built

`src/` holds the content (wording, timings, cues, Q&A and open items in `src/content.js`), the slide layouts and the script chapters; `docs/presentation-kit/` renders the HTML deck and prints both PDFs. Until 20 October the v3 files came from Liu Jin's build outside this repository (HTML plus a Typst PDF); their content, with Eric's spoken wording on top, was then ported back into `src/`.

Commit a changed version only when the content changes. The PDFs and the HTML are binary for Git, so every commit adds about 3.5 MB to the history.

## Corrections still to check

- **Demo step 3** says today's environment "was built from exactly these images". The environment was started by [run 39198123242](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39198123242) on `prod` at `5e83d63`; check that it matches the promotion in #564 before saying it.
- **Demo step 1** expects at least one notification for the demo buyer. Checkout is off on the SWE5001 session (`BLOCBUY_STRIPE_CHECKOUT_ENABLED = "false"` in `infra/swe5001/runtime/services.tf`), so a purchase cannot create one; seed one, or use the run book's fallback line and recording.
