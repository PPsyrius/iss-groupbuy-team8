# Independent review corrections

[Focused red/green](race-red-green.json): two deterministic stale-failure-publication cases fail on the measured v3 implementation, then all **31 tests pass** with ordered atomic updates. Equal/newer cooldown deadlines and active probes are preserved, including monotonic wraparound. The outage integration method gets an isolated Spring context after completion; earlier full-run failures remain [retained](../verification-v3-failed/README.md).

Reviewed package SHA-256 is `07f46aa3998d0e20f46dfb019f3c71dfef52d7aa5d1f9c206e7cf8dc64fdcb92`. It contains functional changes after the sixteen-case ABBA snapshot; those runtime results are not attributed to this newer source. Focused/package success alone is not full verification or startup/fault-latency acceptance.

[Derived GC-max correction](backoff-v3-gc-max-correction.json) fixes summation across timer-max labels without rewriting frozen inputs or changing HTTP counts, latency or gate outcomes. All sixteen cases were checked; fourteen derived gauge records change. The corrected analyzer is separately named and was not used to overwrite the original audit. Sampled timer maxima are not exact individual GC pauses or complete phase maxima.
