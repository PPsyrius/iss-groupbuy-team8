# SWE5001 presentation

Team 8's deck and speaker script for the SWE5001 final presentation on Wednesday 21 October 2026, 16:15–16:55 SGT (Zoom). Tracked in #461. `dist/` holds the version to present (v3, built outside this repository); `notes/` holds hand-written speaker notes.

| File | What it is |
| --- | --- |
| `dist/swe5001-presentation.html` | The deck to present: 24 main slides and 17 backups (BX, B1a–B15), self-contained. Open it in Chrome or Edge. Keys: → / Space next, ← back, S speaker notes with a pace timer, B or X backup index, O overview, F fullscreen, `.` blank screen. |
| `dist/swe5001-presentation.pdf` | The same deck as a 41-page PDF, typeset with Typst. A static fallback if the HTML cannot be shared, and the readable copy on GitHub. |
| `dist/swe5001-presentation-script.pdf` | Speaker script and demo run book: run sheet, one card per slide with wording and timing, the demo steps, contingencies, timeline, open items, operator commands and the backup list. |
| [`notes/speaker-script-eric.md`](notes/speaker-script-eric.md) | Eric's segment 3 (slides 7–11) and demo (slide 23, steps 1–4) reworded for speaking: same facts, terms and slots, with transitions, callbacks and cues. Corrects two run-book lines (see "Corrections" below). |
| [`notes/qa-eric.md`](notes/qa-eric.md) | Eric's Q&A prep for segment 3 and the demo: 26 likely questions with 30–45 s answers, the backup slide to open and the source of each answer. |

## State of the content (19 October)

- Results are final. V3 HTTP auto-scaling ran on AWS on 19 October: commerce scaled 1 → 3 tasks on its own and served about 455 requests per second at server p95 under 50 ms. The load generator hit its limit first, so it is not V3 acceptance ([evidence](../evidence/20261019-v3-autoscaling/README.md)).
- Queue scaling, self-healing, the poison message, the identity outage and the buyer journey end to end did not run on AWS. Slide 5 and backup B12 say "not run on AWS", with the local result where one exists ([15 October local rehearsal](../evidence/20261015-local-failure-rehearsal/README.md)).
- The live demo has three steps: use cases, auto-scaling under a k6 load test, and the promotion pipeline. Stopping a task, the identity outage and the event backlog were cut because they were not rehearsed on AWS; slide 23 says they were tested locally.
- The effort split on slide 6 (12.5 / 11.5 / 9 / 8.5 / 8.5 person-days) is agreed by all five members.
- 20 October: no load test runs during the talk. Demo step 2 shows the live CloudWatch dashboard (commerce at its standing 2 tasks), then the graphs of the 19 October run on AWS; the slide 23 footer says so. A live V3 run would need the commerce floor lowered to 1 and a fresh preflight minutes before the load (k6 validates the snapshot in `perf/k6/lib.mjs`; `infra/swe5001/scripts/http-preflight.sh` only captures it), which the team judged too much to operate on air. Slide 23 is the only slide that changed. The script also rewords Liu Jin's slides 12–15 and the demo lines, adds Liu Jin's extended Q&A bank, and shows promotion #564. It carries three earlier wording fixes: the spoken text for slides 8, 17 and 22 no longer promises failure drills in the live demo.
- Still open: fallback recordings for demo steps 1 and 3, the slide 16 choice (UML sequence or backup B15) at the 20 October rehearsal, and alarm e-mail confirmation (#515).

## How these files are built

The files come from a build outside this repository: one content file (wording, timings, cues and the results block), shared slide renderers that emit both the HTML and the Typst slides, and a Typst script that reads data exported by the HTML build. The UML diagrams are the ones in `docs/presentation-kit/diagrams/`. The generator in `src/` is a separate, earlier source and does not reproduce these files: `npm run all` would overwrite `dist/` with the older deck, so update `dist/` by replacing its three files with the external build's output under the same names. That build names its files with a `-v3` suffix, and the script's cues still use those names (`swe5001-presentation-v3.html` is `dist/swe5001-presentation.html`).

Commit a changed version only when the content changes (after the 20 October rehearsal, and once after the talk if anything is corrected). The PDFs and the HTML are binary for Git, so every commit adds about 3.5 MB to the history.

## Corrections found while preparing the spoken script (19 October)

These are in the built PDF and HTML, which are generated outside this repository, so the owners need to apply them at the next rebuild:

- **Demo step 4 and slide 23** say the three failure drills were tested locally. The 15 October local rehearsal ([evidence](../evidence/20261015-local-failure-rehearsal/README.md)) covered the identity outage and the poison message only; stopping a server (V5) is "not run on AWS" with no local result (slide 5, B12).
- **Slide 8 and slide 17** promised drills "live"; the 20 October script update fixed both. The identity-outage Q&A answer ("We show this live") should get the same fix.
- **Demo step 3** says today's environment "was built from exactly these images". The environment was started by [run 39198123242](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39198123242) on `prod` at `5e83d63`; check that it matches the promotion in #534 before saying it.
- **Demo step 1** expects at least one notification for the demo buyer. Checkout is off on the SWE5001 session (`BLOCBUY_STRIPE_CHECKOUT_ENABLED = "false"` in `infra/swe5001/runtime/services.tf`), so a purchase cannot create one; it has to be seeded, or the step skips the notifications view.
- **Demo step 2** now starts from one task (the 20 October update: floor lowered to 1 at 16:05 for the k6 preflight); `notes/speaker-script-eric.md` follows that wording.
