# V4 R3 — interrupted original collection; later effects reconciled

Refs #458/#459/#448, NFR-52/63/64/66. This dated amendment records retained evidence through **22:57:36 SGT on 13 November**, not a new cloud execution or a strict scaling pass. The [JSON record](v4-r3-interrupted.json) supplies actual private-root-relative source paths and SHA-256 hashes; only filtered facts are published. Earlier [C1 admission failure](v4-r3-setup-c1.md), [R2 scaling failure](v4-r2.md), R1 and identity outcomes remain unchanged.

## Original R3 interruption

The canonical source was signed **`8bec6bbea50cee05487bf33476730489653b4355`**, with unchanged application release `3a893036d70d028b29ae13ebf820283571c1ae00`; separately reviewed operator controls are not an application promotion. The one temporary existing-commerce count **2→6** request was recorded at **20:07:37.254547 SGT** (`12:07:37.254547Z`). Readiness at **20:10:17.702618** followed a real **60.353-second continuous healthy hold**; no forced notification count or consumer/policy change is represented.

At **20:12:26.103579 SGT**, the canonical collector exited **255** after a read-only ECS task waiter reported “Connection was closed before we received a valid response”; the provider endpoint is omitted. Global `AWS_MAX_ATTEMPTS=1` provided no SDK retry for that network read. The original result remains failed/not accepted, with **`remoteSqlOutstanding`** retained, no automatic-four provenance and no asserted remote-process cancellation. This is not a completed collector or a four-worker scaling acceptance result.

Read-only recovery found the **actual stopped seed task's receipt**, not a new seed: cohort `v4-20261113-c`, **20,000 teams and memberships**, seeded **20:11:57.124490**, expiry **20:12:57.124490 SGT**. The empty original local seed file is not presented as successful collection. Independent saved-count restoration was healthy at **20:15:01.640618**; later SQL reconciliation did not hold that restoration hostage.

The restoration **request** was recorded at **20:12:26.870306 SGT**, **30.254184 seconds before seed expiry**. The restorer responded to the failed capture rather than maintaining the requested six-count operating condition into expiry. This is therefore **not a valid six-producer-through-expiry capacity test**, and no producer-throughput failure or hard capacity ceiling follows. The request timestamp is not an actual task-stop/running-two timestamp; only later healthy saved-count restoration is established here.

The fixed read-only reconciliation exited **0 at 20:22:54.409295 SGT**, wall **79.59 s**; its database observation was **20:22:24.865029**. Exactly **20,000 each** teams/expired teams, outbox/published events, notifications and consumer markers were present, with **zero failed outbox events**. **No reseed occurred.** This proves later complete source/effect reconciliation, not original collector success, qualification removal or four-worker-before-drain acceptance.

## Separate captures and restored pause

Private **`cloudwatch-v4-c`** and **`cloudwatch-v4-c-reconciled`** each retain **23 regional queries and six PNGs**, 21 series with data and no image errors. Their original and reconciliation windows remain separate; neither package overwrites the other. Missing series are not asserted to be zero, and these captures do not turn interrupted collection into scaling acceptance.

Final **normal/V4-off/trace-off** restoration was stable at **20:51:28.552137 SGT**, wall **251.522 s**, preserving released digests, commerce **1,024 CPU units/2,048 MiB/five connections** and unchanged identity/notification definitions. **Safe-to-sleep completed at 20:52:46.900718**: healthy normal runtime, no outstanding laptop-dependent fault/load/SQL task, generator **stopped**, runtime/database/queues retained and **no teardown**. The **663-file** private archive was readback-verified, SHA-256 **`742fd5ffd2d777d09df4faa69a5558b5d54879b58b29866c951fae96998935de`**; the safe receipt and readback both hash to **`64712e71c08a9b7ecb2d3fd39a19b422fc095018ba4967e5219feae24d1de795`**.

The distinct private `v4-20261113-d` context was prepared but inactive, without execution authorization or seed at that checkpoint. **No automatic 22:00 resume timer** was installed. The older C1 timing amendments are historical authorization states, not a claim that the interrupted collection met their provisional deadlines.

## Owner return and explicit nightly-teardown pause

The owner reported returning at **22:42**, followed by a laptop restart. Fresh SSO verification was **authenticated at 22:54:47.221631 SGT**; the saved application session returned **HTTP 200 at 22:55:51.457969**, so a new application login was unnecessary. These are authentication observations, not workload or scaling tests.

Live inspection by the parent found the **23:00 SGT nightly teardown** enabled. The owner explicitly selected **“Pause nightly teardown”**; the parent's **22:57:36.5195642 SGT** receipt records only repository variable **`ENABLE_SWE5001_NIGHTLY_DOWN: true→false`**, with readback verified, no workload/teardown and no automatic re-enable scheduled. The parent also reported no running/queued environment jobs; this documentation reviewer did not independently query GitHub or verify that job listing. This pause protects retained runtime/data; it is not runtime cleanup or a new workload authorization.

## Next execution remains separate

The latest owner instruction permits ongoing fix/retry, with bounded independent restoration for each attempt; it does not authorize teardown or application/consumer/policy changes. The public next name remains **V4 R3** as corrected by the owner, with unique timestamps and private artifact labels distinguishing the interrupted `c` cohort from unused `d`; earlier execution is not renamed. **No fresh R3 count increase or seed is established by this amendment**, and no live fix or cloud pass is claimed.

Retained child preparation records **51 credential-free checks**; a separate retained local PostgreSQL/mapper receipt records **ten checks**. The handoff additionally reports **62 next-configuration checks**, not independently rerun or source-receipt-verified here. These are distinct offline/local verification levels, not this documentation review's test results or live retry acceptance. The child preparation's MIN600=MAX600 completion blocker and retry-classification tests require separately reviewed future controls; no original analyzer gate is waived. Identity retry/payment/replay remain deferred/not run, and existing V3/report/#663 records are untouched.

Documentation verification: **56 initial receipt/hash assertions passed**, with all **21 final manifest hashes** and the **30.254184-second request/expiry difference** subsequently checked; **165 local Markdown target paths** existed, five-file privacy/whitespace scans passed, the same-author session log retained **11 bullets**, and `git diff --check` passed. A validation-script rerun initially used Windows' default text encoding and was corrected to explicit UTF-8; no evidence bytes changed for that correction. No operational test, AWS/GitHub call, staging, commit or push was performed by this reviewer.
