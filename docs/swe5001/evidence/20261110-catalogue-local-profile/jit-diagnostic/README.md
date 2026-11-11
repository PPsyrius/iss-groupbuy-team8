# C1-only diagnostic — rejected

Six independently audited local cases used `-XX:TieredStopAtLevel=1` with ordinary full logging. Baseline cold warmup still failed; warmed 400 RPS failed badly; v2's healthy control failed, so its warmed faults were refused. See [raw-sample audit](analysis.json) and [case receipts](receipts.json). Production compiler settings remain unchanged.

JFR summaries describe sampled work and GC, not causal attribution. Private recordings, raw streams, GC logs and VM flags stay outside Git, with hashes retained. This is neither default-JVM acceptance nor a completed matched ABBA; no workload/latency/error/drop gate was relaxed.
