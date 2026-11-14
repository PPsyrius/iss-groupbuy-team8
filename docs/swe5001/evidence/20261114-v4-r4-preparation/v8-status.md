# V8 lifecycle evidence status — 14 November 2026

- [x] Collect create, smoke and destroy durations from at least three clean runtime sessions.
- [x] Read back all four referenced GitHub runs: completed/successful at **03:49:56 SGT, 14 November**.
- [ ] Meet the full distributed startup target of under 30 minutes: **failed**, with retained measurements of **31m37s** and **32m17s**; the monolith's **12m46s** is not distributed acceptance.
- [ ] Claim the current retained session's teardown/absence: **not performed**; historical destruction is not current absence.

The three-run evidence-collection task is **done**, not pending another rebuild. Overall V8 acceptance is not marked passed. See [machine-readable status](v8-status.json) and the [canonical timing record](../../impl-verification-observability.md#v8-rebuild-from-zero), which links the original job logs and distinguishes initial apply, smoke, destroy and full-up wall time. No new cloud cycle or teardown was started for this status update, and no database-content recovery is claimed.
