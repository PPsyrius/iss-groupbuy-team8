# V4 R2 — explicitly revised fixture-only trial

Refs #458/#459/#448. The owner requested the quickest approach with the least changes, then explicitly authorized a fixture-only R2 of **up to 20,000 real expiring teams**, after a source/consumer rate review, and up to **90 additional session minutes**. The separately readback-verified generator autostop is now `08:58Z` / **16:58 SGT**; its timer does not destroy runtime. Retain a 25-minute cleanup reserve. The owner's 17:30 planning frame is not a guaranteed all-gates-pass deadline or additional promotion authority.

## Boundaries

- Preserve the original **2,000-team V4 R1 failure**, its unchanged inputs and original outputs; a revised workload cannot retroactively pass that run.
- Keep the application's released images, resources, pools, consumer concurrency, 50-message-per-worker target, scaling policy/thresholds and scale-before-final-drain gate unchanged. No direct SNS injection, consumer slowdown, forced workers, IAM expansion or application promotion.
- Preserve the ordinary 2,000-team default. Bind the revised count explicitly through fixture confirmation, source receipt, collector/report and analyzer, with exact matching source/effect counts and zero failed outbox rows; unknown/mismatched counts must fail closed.
- Use a new label/private results directory and tested, reviewed tooling with its actual source identity. No reseeding after ambiguous failure; inspect that original run-scoped report first.
- Complete producer/effect reconciliation and final queue drain before acceptance; a momentarily empty arrival-paced queue is insufficient reason to end the larger capture early.

## Rate review and stop rule

R1 recorded all 2,000 relay results and notification effects. Its 21 relay batches were nineteen batches of 100 plus 88 and 12; inter-batch gaps were 0.438–2.492 seconds, median 2.3045 seconds. Effects spanned 43.445 seconds, an achieved 46.035/s with a one-second peak of 99. This is observed throughput, **not measured sustainable consumer capacity**. The two-second relay delay is per JVM: the shared lease is released after a batch, so two commerce JVMs can alternate without a hard cluster-wide two-second pause. Fifty events/s must not be presented as a proven cluster ceiling.

At unchanged achieved throughput, 20,000 effects would span roughly 434 seconds (7.24 minutes), making one longer trial a legitimate duration hypothesis. Count alone cannot create overload if one consumer keeps pace. Retain actual minute-average backlog-per-task data, queue age, real desired/running task counts and original scaling activities. After four completed source-active minute buckets, allow metric publication latency, then stop pursuing scaling acceptance if substantial upstream work remains but backlog repeatedly clears without a developing sustained breach: add no more fixtures and let existing work drain. Continue unchanged only if the trial develops a genuine scaling signal within the approved window.

R1's 42.25-second analyzer output identifies the start of its sampled final queue-quiet suffix, **not a proven complete-pipeline drain time**: its final relay-result log occurred after that first zero sample. R2 must reconcile source completion and effects rather than extrapolating that sampled instant. V4 R2 has not been seeded or accepted at this documentation checkpoint.
