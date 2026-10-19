# SWE5001 presentation v3 (the version to present)

Team 8's deck and speaker script for the SWE5001 final presentation on Wednesday 21 October 2026, 16:15–16:55 SGT (Zoom). Tracked in #461. This folder holds the built files only; it supersedes `../dist/`, which still has 26 unfilled "FILL 19 Oct" markers and predates the 19 October results.

| File | What it is |
| --- | --- |
| `swe5001-presentation-v3.html` | The deck to present: 24 main slides and 17 backups (BX, B1a–B15), self-contained. Open it in Chrome or Edge. Keys: → / Space next, ← back, S speaker notes with a pace timer, B or X backup index, O overview, F fullscreen, `.` blank screen. |
| `swe5001-presentation-v3.pdf` | The same deck as a 41-page PDF, typeset with Typst. A static fallback if the HTML cannot be shared, and the readable copy on GitHub. |
| `swe5001-presentation-script-v3.pdf` | Speaker script and demo run book: run sheet, one card per slide with wording and timing, the demo steps, contingencies, timeline, open items, operator commands and the backup list. |
| `speaker-script-eric.md` | Eric's segment 3 (slides 7–11) and demo (slide 23, steps 1–4) reworded for speaking: same facts, terms and slots, with transitions, callbacks and cues. Corrects two run-book lines (see "Corrections" below). |
| `qa-eric.md` | Eric's Q&A prep for segment 3 and the demo: 26 likely questions with 30–45 s answers, the backup slide to open and the source of each answer. |

## State of the content (19 October)

- Results are final. V3 HTTP auto-scaling ran on AWS on 19 October: commerce scaled 1 → 3 tasks on its own and served about 455 requests per second at server p95 under 50 ms. The load generator hit its limit first, so it is not V3 acceptance ([evidence](../../evidence/20261019-v3-autoscaling/README.md)).
- Queue scaling, self-healing, the poison message, the identity outage and the buyer journey end to end did not run on AWS. Slide 5 and backup B12 say "not run on AWS", with the local result where one exists ([15 October local rehearsal](../../evidence/20261015-local-failure-rehearsal/README.md)).
- The live demo has three steps: use cases, auto-scaling under a k6 load test, and the promotion pipeline. Stopping a task, the identity outage and the event backlog were cut because they were not rehearsed on AWS; slide 23 says they were tested locally.
- The effort split on slide 6 (12.5 / 11.5 / 9 / 8.5 / 8.5 person-days) is agreed by all five members.
- Still open: fallback recordings for demo steps 1–3, the slide 16 choice (UML sequence or backup B15) at the 20 October rehearsal, and alarm e-mail confirmation (#515).

## How these files are built

The files come from a build outside this repository: one content file (wording, timings, cues and the results block), shared slide renderers that emit both the HTML and the Typst slides, and a Typst script that reads data exported by the HTML build. The UML diagrams are the ones in `docs/presentation-kit/diagrams/`. The generator in `../src/` is a separate, earlier source and does not reproduce these files.

Commit a changed version only when the content changes (after the 20 October rehearsal, and once after the talk if anything is corrected). The PDFs and the HTML are binary for Git, so every commit adds about 3.5 MB to the history.

## Corrections found while preparing the spoken script (19 October)

These are in the built PDF and HTML, which are generated outside this repository, so the owners need to apply them at the next rebuild:

- **Demo step 4 and slide 23** say the three failure drills were tested locally. The 15 October local rehearsal ([evidence](../../evidence/20261015-local-failure-rehearsal/README.md)) covered the identity outage and the poison message only; stopping a server (V5) is "not run on AWS" with no local result (slide 5, B12).
- **Slide 8** ends with "We'll show you that live", **slide 17** says "You'll see the dead-letter queue in the demo", and the identity-outage answer says "We show this live". Those drills were cut from the live demo on 19 October.
- **Demo step 3** says today's environment "was built from exactly these images". The environment was started by [run 39198123242](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39198123242) on `prod` at `5e83d63`; check that it matches the promotion in #534 before saying it.
- **Demo step 1** expects at least one notification for the demo buyer. Checkout is off on the SWE5001 session (`BLOCBUY_STRIPE_CHECKOUT_ENABLED = "false"` in `infra/swe5001/runtime/services.tf`), so a purchase cannot create one; it has to be seeded, or the step skips the notifications view.
