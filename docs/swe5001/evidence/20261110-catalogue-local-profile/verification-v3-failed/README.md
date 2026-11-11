# V3 full-verification failures

[Failure receipts](failures.json) preserve two distinct failed full-reactor attempts: an unnecessary SpotBugs suppression, then eight cache integration failures caused by outage cooldown leaking across methods. Neither is accepted as a passing build, complete coverage result or runtime fix. The first reactor could retain stale skipped-module targets; its mixed totals/aggregate are excluded. The second precleaned all modules and has only fresh partial reports.

The unused annotation was removed. Separate [review regressions](../review-corrections/race-red-green.json) also reproduce stale failure publication and pass after atomic deadline ordering. The outage method now uses Spring context isolation; no sleep, production reset hook, assertion removal or gate relaxation was added. Full verification of that reviewed snapshot is tracked in the [living matrix](../MATRIX.md).
