# Failure-triggered backoff v3 — focused checkpoint

[Red/green summary](red-green.json): healthy concurrent cache calls fail the new regression on fixed-slot v2 (19 tests / one failure). V3 removes the fixed slots and passes 29 focused tests plus commerce packaging. Sources match the machine-formatted inputs; artifact/source hashes are retained.

Tests cover cooldown boundaries/wraparound, repeated and null failures, shared read/write probes, stale transitions and unexpected-exception release. This is **focused/package evidence only**, not full-reactor coverage, runtime non-regression, target health or AWS acceptance. [Design and limits](../../../../../backend/docs/design/best-open-deal-cache.md#same-day-supersession--failure-triggered-backoff-v3).
