# SWE5001 presentation v3 (the version to present)

Team 8's deck and speaker script for the SWE5001 final presentation on Wednesday 21 October 2026, 16:15–16:55 SGT (Zoom). Tracked in #461. This folder holds the built files only; it supersedes `../dist/`, which still has 26 unfilled "FILL 19 Oct" markers and predates the 19 October results.

| File | What it is |
| --- | --- |
| `swe5001-presentation-v3.html` | The deck to present: 24 main slides and 17 backups (BX, B1a–B15), self-contained. Open it in Chrome or Edge. Keys: → / Space next, ← back, S speaker notes with a pace timer, B or X backup index, O overview, F fullscreen, `.` blank screen. |
| `swe5001-presentation-v3.pdf` | The same deck as a 41-page PDF, typeset with Typst. A static fallback if the HTML cannot be shared, and the readable copy on GitHub. |
| `swe5001-presentation-script-v3.pdf` | Speaker script and demo run book: run sheet, one card per slide with wording and timing, the demo steps, contingencies, timeline, open items, operator commands and the backup list. |

## State of the content (19 October)

- Results are final. V3 HTTP auto-scaling ran on AWS on 19 October: commerce scaled 1 → 3 tasks on its own and served about 455 requests per second at server p95 under 50 ms. The load generator hit its limit first, so it is not V3 acceptance ([evidence](../../evidence/20261019-v3-autoscaling/README.md)).
- Queue scaling, self-healing, the poison message, the identity outage and the buyer journey end to end did not run on AWS. Slide 5 and backup B12 say "not run on AWS", with the local result where one exists ([15 October local rehearsal](../../evidence/20261015-local-failure-rehearsal/README.md)).
- The live demo has three steps: use cases, auto-scaling under a k6 load test, and the promotion pipeline. Stopping a task, the identity outage and the event backlog were cut because they were not rehearsed on AWS; slide 23 says they were tested locally.
- The effort split on slide 6 (12.5 / 11.5 / 9 / 8.5 / 8.5 person-days) is agreed by all five members.
- 20 October: the demo load starts from one commerce task, as on 19 October. The k6 V3 preflight accepts only a stable one-task deployment with scaling bounds 1..6 (`perf/k6/lib.mjs`, `infra/swe5001/scripts/http-preflight.sh`), so at about 16:05 the commerce floor is lowered from its standing 2 to 1, and the preflight is captured at 16:23. Expected: 2 tasks around 16:39, 3 around 16:42. Slide 14's "2–6" is the standing configuration and stays. Only the script changed (slide 13 cue, demo steps 2 and 4, §6 preconditions, §7 contingencies, §8 timeline, §9 open items, Appendix A.7, one Q&A row); the slides are unchanged. This rebuild also carries three wording fixes already in the source: the spoken text for slides 8, 17 and 22 no longer promises failure drills in the live demo.
- Still open: fallback recordings for demo steps 1–3, the slide 16 choice (UML sequence or backup B15) at the 20 October rehearsal, and alarm e-mail confirmation (#515).

## How these files are built

The files come from a build outside this repository: one content file (wording, timings, cues and the results block), shared slide renderers that emit both the HTML and the Typst slides, and a Typst script that reads data exported by the HTML build. The UML diagrams are the ones in `docs/presentation-kit/diagrams/`. The generator in `../src/` is a separate, earlier source and does not reproduce these files.

Commit a changed version only when the content changes (after the 20 October rehearsal, and once after the talk if anything is corrected). The PDFs and the HTML are binary for Git, so every commit adds about 3.5 MB to the history.
