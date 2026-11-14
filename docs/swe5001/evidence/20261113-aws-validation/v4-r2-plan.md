# V4 R2 — explicitly revised fixture-only trial

Refs #458/#459/#448. The owner requested the quickest approach with the least changes, then explicitly authorized a fixture-only R2 of **up to 20,000 real expiring teams**, after a source/consumer rate review, and up to **90 additional session minutes**. The initially extended generator autostop was `08:58Z` / 16:58 SGT; the owner subsequently explicitly approved a latest stop at that checkpoint of `09:30Z` / **17:30 SGT**, with a 25-minute cleanup reserve and instructions to start as soon as validation completes, not wait for that window. Both bounded timer changes were read back; the later 18:15 amendment is recorded below. The timer does not destroy runtime, guarantee all gates will pass or authorize promotion.

## Boundaries

- Preserve the original **2,000-team V4 R1 failure**, its unchanged inputs and original outputs; a revised workload cannot retroactively pass that run.
- Keep the application's released images, resources, pools, consumer concurrency, 50-message-per-worker target, scaling policy/thresholds and scale-before-final-drain gate unchanged. No direct SNS injection, consumer slowdown, forced workers, IAM expansion or application promotion.
- Preserve the ordinary 2,000-team default. Bind the revised count explicitly through fixture confirmation, source receipt, collector/report and analyzer, with exact matching source/effect counts and zero failed outbox rows; unknown/mismatched counts must fail closed.
- Use a new label/private results directory and tested, reviewed tooling with its actual source identity. No reseeding after ambiguous failure; inspect that original run-scoped report first.
- Complete producer/effect reconciliation and final queue drain before acceptance; a momentarily empty arrival-paced queue is insufficient reason to end the larger capture early.

## Rate review and stop rule

R1 recorded all 2,000 relay results and notification effects. Its 21 relay batches were nineteen batches of 100 plus 88 and 12; inter-batch gaps were 0.438–2.492 seconds, median 2.3045 seconds. Effects spanned 43.445 seconds, an achieved 46.035/s with a one-second peak of 99. This is observed throughput, **not measured sustainable consumer capacity**. The two-second relay delay is per JVM: the shared lease is released after a batch, so two commerce JVMs can alternate without a hard cluster-wide two-second pause. Fifty events/s must not be presented as a proven cluster ceiling.

At unchanged achieved throughput, 20,000 effects would span roughly 434 seconds (7.24 minutes), making one longer trial a legitimate duration hypothesis. Count alone cannot create overload if one consumer keeps pace. Retain actual minute-average backlog-per-task data, queue age, real desired/running task counts and original scaling activities. After four completed source-active minute buckets, allow metric publication latency, then stop pursuing scaling acceptance if substantial upstream work remains but backlog repeatedly clears without a developing sustained breach: add no more fixtures and let existing work drain. Continue unchanged only if the trial develops a genuine scaling signal within the approved window.

R1's 42.25-second analyzer output identifies the start of its sampled final queue-quiet suffix, **not a proven complete-pipeline drain time**: its final relay-result log occurred after that first zero sample. R2 must reconcile source completion and effects rather than extrapolating that sampled instant.

## Signed tooling and execution

The owner signed `8bec6bbea50cee05487bf33476730489653b4355`, verified `G`, and a new clean execution copy was bound to it. Four JavaScript files changed during the commit process; the source guard refused the old pre-commit hashes, then Node's 55 tests and the real-schema PostgreSQL integration were rerun successfully against the exact signed bytes. All SQL/shell files remained byte-identical to their passing proofs (31 seed and 38 tooling shell checks); signature/clean-source gates were not bypassed, and no private signing key was copied. The ordinary 2,000-team default remains supported.

R2 used `V4_EXPECTED_COUNT=20000`, `CONFIRM_V4_SEED=20000`, `V4_MIN_SECONDS=600`, and the unchanged 1,800-second maximum collector window. Its signed seed receipt proves exactly 20,000 unpaid teams/memberships at `08:05:24.941261Z`, due at `08:06:24.941261Z`, with no seeded payment/order/refund and the released commerce digest unchanged. Revised reporting retained incomplete intermediate receipts and waited for exact producer/effect completion followed by at least three quiet samples spanning thirty seconds. The four-full-bucket checkpoint was an interim observation, not an early pass; the final result follows.

## Completed R2 — strict scaling failed

The [public evidence](v4-r2.md) and [JSON record](v4-r2.json) retain the original collector exit **0**, **832.297 s**, completed **16:18 SGT** (`08:18:06.414504Z`). Exactly **20,000 each** teams/expired teams, outbox events, published events, notifications and consumer markers reconciled, with zero failed outbox events and sampled DLQ counts zero. Maximum observed notification tasks stayed **one**, no autoscaling activities occurred, and **scale-to-four-before-final-drain and drain-after-scale failed**. This is not application failure, a full V4 pass or a correction of R1's failure.

The four complete source-active minute buckets published **3,200/3,500/3,300/3,800**, with policy Average backlog/task **0/0/55/8** against target **50** and three 60-second evaluation periods. One brief breach was insufficient; neither a producer-rate ceiling nor consumer capacity is established. The analyzer's **341.059 s** is only the start of the final sampled quiet suffix, not complete-pipeline drain. Source/effect reconciliation was recorded later, followed by four post-report quiet samples over **36 s**, satisfying the separate three-sample/30-second minimum.

## Later owner steering — preparation is not execution authority

After R2 failed, the owner explicitly allowed finishing **identity and V8 while retaining both V4 failures**. Background **R3 preparation** is permitted, but **execution and resource changes are not authorized**. Four standalone producers were rejected because their connection budget would not fit if commerce autoscaled to six tasks; a service-managed alternative is under review, not launched. No additional count-only seed, consumer/policy manipulation or forced worker is approved by this record; any executable proposal needs separate scoped review and authorization.

The owner asked for immediate progress and completion well before **18:20 SGT**; the parent selected and reports readback of an **18:15 SGT** (`10:15Z`) hard stop, with a **25-minute cleanup reserve**, **17:50 SGT** latest active-work cutoff and the aim to finish sooner. This supersedes the earlier timer, not the safety gates. Timer configuration is not evidence of completed work or runtime teardown; identity, V8, final restoration, preservation and independent absence remain pending parent results.
