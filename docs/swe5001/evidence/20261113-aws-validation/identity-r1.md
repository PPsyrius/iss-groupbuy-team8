# Identity R1 — START outage probe failed; payment and replay not run

Refs #459. [Machine-readable evidence](identity-r1.json) binds seventeen approved private records by SHA-256, including later shutdown diagnosis, completed load/capture, provider reconciliation and owner disposition. This is offline evidence preparation, with no AWS calls, staging, commit or push; no application defect, full identity pass, V8 completion, teardown or deadline fulfillment is claimed.

## Actual fault and failed probe

The identity desired-count change **one → zero** completed at **17:09:34.866 SGT** (`09:09:34.866120Z`); service zero was verified at **17:09:45.940 SGT**. The real START probe at **17:09:46.068 SGT** returned **HTTP 200 / `COMMON_200_000`**, not the expected **500**. The probe gate therefore **failed**. The later diagnosis confirms a shutdown-gating race: service counts reached zero before the old application shut down, not proof of application unavailability. Cached START, wrong-route and wrong-application-code explanations were rejected; the specific Service Connect mechanism is not established.

| Old identity task/application milestone | SGT |
| --- | --- |
| Task STOPPING | 17:09:44.993 |
| Service zero observed / successful START recorded | 17:09:45.940 / 17:09:46.068 |
| JVM graceful shutdown started → completed | 17:10:51.571 → 17:10:51.574 |
| Execution stopped | 17:11:02.864 |
| Whole task STOPPED | 17:11:57.803 |

The read-only diagnosis recorded at `09:17:01.104491Z` matched distributed source profiles, route configuration and released images. It did **not** directly bind the request trace to the old task or establish the particular Service Connect routing mechanism; those stronger claims are not made.

Parent-reported browser history is retained: B1 failed on a missing card field, B2/B3 on strict submit-selector resolution, and B4 reached readiness with corrected form-scoped button selection. B4's payment guard blocked after the unexpected START result: **provider payment, signed webhook outage/recovery and duplicate replay were NOT RUN**. Readiness and failed selector attempts are not payment evidence.

## Independent restoration and state limits

Independent restoration was armed before the fault for **17:10:53.827 SGT**, did not depend on browser/observer cleanup and requested desired count **one** at **17:10:55.464 SGT**, without waiting for the provider. The subsequently available receipt reports healthy/saved-desired-restored at **17:13:11.654 SGT**, but **217.827502 s** from fault to healthy **fails the existing 180-second gate**. Healthy restoration is not a timing pass or complete drill acceptance.

| Scoped observation | Before (`16:43:36.832 SGT`) | After failed probe (`17:12:16.854 SGT`) |
| --- | ---: | ---: |
| Fixture payment state | created | created |
| Intents for actor/product | 1 | 2 |
| Payment webhook receipts | 0 | 0 |
| Participation receipts / members / orders / refunds | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 |
| Product stock | 300 | 300 |

The after-report task exited **0**, but the **1→2 intent-count difference forbids an unchanged-state claim**. Its cause is not established: the baseline predates the probe. No provider event was supplied to either report, so null event-scoped counts are not treated as verified zeros. No settlement occurred for the reported fixture.

## Completed load and capture amendment

Later raw review proves **1,800 workload GETs plus one setup GET**, zero invalid/non-2xx/dropped iterations and all original k6/Docker-wait/logger/monitor/analyzer exits **0**. The 15-minute load ran `09:00:19.072764119Z`–`09:15:20.587038066Z` (**17:00–17:15 SGT**). The generator archive SHA-256 is `ae4a4300a7c55f2499b2b1d40ce0c4aa49e6edb93f74c483223cc7301ea732b3`; **18 individual files** were readback-verified.

The completed **`identity-b-observer`** has **87 samples**, maximum gap **17.790691 s**, no reported collection errors and coverage from `08:58:50.390908Z` through `09:23:50.240218Z`, bracketing the full load. The preserved original **`identity-observer`** is a distinct **87-sample setup capture** completed around 17:06 SGT; it is not the full-load proof. Earlier pending-capture wording was accurate then. Completed browse/capture gates do **not** turn the missing START 500 or failed 180-second restoration gate into a pass.

At **`09:58:32.137492Z` / 17:58 SGT**, provider reconciliation matched the original test-mode fixture and confirmed actual **expired / unpaid** status, no controller payment submission and **no new fixture**. No payment or signed webhook recovery/duplicate replay was run. The earlier scoped 1→2 intent-count difference remains unattributed.

## Owner disposition and deferred cleanup

The owner explicitly approved proceeding to **V4 R3 while deferring identity retry**, retaining both identity failures and unexecuted payment/replay gates. Latest parent steering authorizes one existing commerce service count **2→6 within its existing maximum** and **one 20,000-team fixture**, with unchanged consumers/policies/images/pools, **no standalone commerce producers**, and notification growth to four required to be **automatic**. Existing db-init seed/report slots are authorized, not extra application workers. This records authorization only: at the supplied checkpoint preparation awaited plan review, and no R3 start/outcome is asserted without parent proof.

The parent requires commerce-count restoration by **18:18 SGT** and normal settings/artifacts by **18:25 SGT**. Runtime cleanup/V8 destruction is **deferred until around 19:00 SGT**, with owner acceptance of retained cost; it is not complete or authorized to run now. The latest separately readback-verified generator-only autostop is **18:45 SGT**, as reported by the parent, and does not destroy runtime. Laptop closure requires no active fault and verified artifact transfer. These are deadlines/disposition, not fulfillment or a new ETA.

Final V4-off/trace-off settings, quiet-DLQ verification, fresh final archive readback, deferred teardown, independent absence and the separate generator stop remain unproved by this identity record. Only aggregate outcomes, timestamps, codes and source hashes are published; private resource/account/user/trace/message/event identifiers, cookies, Checkout URLs, raw bodies and provider payloads are omitted.
