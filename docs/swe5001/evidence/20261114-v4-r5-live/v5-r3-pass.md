# V5 R3 — retained pass and verified raw package

**PASS**, the original V5 R3 accepted **13 November 05:31:00.518412Z** after the complete workload, fault, replacement and health captures. This is a review/readback of existing evidence, not another V5 execution. R1's interrupted workload and R2's failed latency/replacement results remain unchanged.

| Gate | Actual original result |
| --- | --- |
| Workload | **60,000** workload GETs, plus one setup GET; **zero invalid, non-2xx or dropped requests**. |
| Original process exits | k6, Docker wait, logger, monitor and analyzer all **0**. |
| Fault | Exactly the approved preflight task stopped around load minute three; desired counts, released definitions and image digests unchanged. |
| Replacement RUNNING | **63.282591 seconds**, within 120 seconds. |
| Replacement ALB healthy | First bound healthy observation **79.457879 seconds**; previous initial observation 73.956638 seconds, within 120 seconds. |
| Latency | p95 **16 ms**, p99 **66 ms**; not an end-to-end expiry measurement. |
| Generator | Maximum retained sample gap 3.645 seconds; peak CPU 66.8% of one core, not 66.8% of its eight-core limit; peak memory 1.14% of 8 GiB. |

Application release `3a893036d70d028b29ae13ebf820283571c1ae00`, historical workload tooling `732c49b5af7526b0d82c5c332df8db9949e8a82e` and the operator fix/configuration base `33a1a9a575c847e37c07f7df2c8cdeb09077c722` are distinct identities. The then-uncommitted runtime corrections were hash-bound separately and later signed; neither the later signed commit nor R5's experimental source/image retroactively becomes V5 R3's execution source. Commerce 1,024 CPU/2,048 MiB, pool five and 120-second slow start were the tested correction, not proof that R2's smaller configuration passed.

The [original final acceptance](v5-r3-pass.json) is byte-identical to `v5-c-acceptance.json`. The [original workload-only browse analysis](v5-r3-original-browse-analysis.json) intentionally still has `awsAcceptanceComplete=false`: fault/health acceptance was a later independent step, not a rewritten workload result. Original generator metadata's `acceptanceComplete=false` is likewise preserved, alongside the final all-gates acceptance.

The original **9,010,054-byte** raw generator archive SHA-256 is **`40ab56a16878ef083ffd0e370c09b726bd35f38e497dc53a4fdd11d08d753357`**. Its archive hash and all **18 manifest members** were independently readback-verified again, including raw samples, summary, generator observations, process results and original analysis. The accepted observer/target-health/raw-review hashes also match. No raw cookie, provider payload or Terraform data was admitted to the selected package.

That unchanged original archive, continuous observer/health data and historical regional metrics accompany V4 R5 and retained V7 R1 failures in the [private encrypted-storage package](private-package.json), archive SHA-256 **`2f2be4d09d30c69a9dd8343f11020b1e696cbaf418d53db54e023cf3500e5f45`**, downloaded and verified. Raw URLs/task identities remain private. This V5 pass does not pass V7/V8/identity, claim current absence, promote a release or establish whole-campaign completion.
