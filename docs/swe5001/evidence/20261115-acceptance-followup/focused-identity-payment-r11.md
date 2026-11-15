# Focused payment-initiation failure and recovery evidence

## Observed result

The requested focused check was observed: the authenticated payment-initiation API returned HTTP 500 while identity was unavailable and HTTP 200 after identity recovered. The failed request committed no intent, team, payment or order; the successful request created exactly one valid intent. This is application payment initiation, not proof that Stripe rejects an already-created Checkout.

All times below are Singapore time on 15 November 2026. Actor B and product P are private aliases; exact identifiers, trace values and response bodies remain in the private source records.

| Step | Actual time (SGT) | Retained observation |
| --- | --- | --- |
| Fault write began | 13:34:16.050018 | Original fault clock recorded before the identity service mutation. |
| Automatic restore request completed | 13:34:57.484248 | Independent restoration requested the original desired count; no payment response was needed to trigger it. |
| Original application shutdown completed | 13:35:17.484000 | Exact initial-task application log establishes shutdown, rather than relying on desired count zero. |
| Payment initiation during outage | 13:35:24.476270–13:35:24.711994 | `POST /api/v1/payments/intents`, `{"operation":"START","businessId":"P"}`, returned **500 / COMMON_500_001** for B. |
| Identity recovery observation | 13:37:03.628152 | Desired/running/pending 1/1/0, target healthy, authenticated profile response 200; observed by approximately **167.578 seconds** after the original fault write. This is not the payment endpoint's recovery observation. |
| Payment initiation after recovery | 13:46:48.747–13:46:48.817 | Same actor, endpoint, START operation and product; returned **200 / COMMON_200_000** with an intent identifier. No Checkout or provider payment was created. |
| Database reconciliation | 13:50:11.635336–13:50:11.647147 | Exact successful intent and the failed-request creation window were inspected independently. |

The down-state row uses the Linux transport's request-issued and receipt-consumed bounds; the actual browser request ran at **13:35:24.629–13:35:24.678 SGT** inside those bounds. Its HTTP 500 trace correlates with `IdentityUnavailableException`. No separate commerce “completed 500” log marker is claimed. The post-recovery row uses the browser's actual request/response timestamps. Original application shutdown was **61.433982 seconds** after the fault; authenticated identity recovery was **167.578134 seconds**, not a payment-endpoint response at that time.

Offline comparison binds the down/up request to the same B/P, START operation, POST path and JSON/`X-BlocBuy-Request: checkout` headers. Before the UP request, runtime metadata at **13:46:28.660037 SGT** records healthy services, normal task-definition/image bindings and no pending tasks; identity matches the fault plan's original definition/image. The browser used same-origin credentials without cookie/storage extraction.

## Database effects

- Failed-request window: **0 START intents, 0 teams, 0 payments and 0 orders**, scoped to B/P and including deleted rows.
- Successful request: **1 exact valid intent**; no fulfilled payment identifier and **0 Checkout payments, teams, orders, refunds, participations or related outbox rows** for that new intent/reserved team.
- Actor/product intent counts were **9 before outage, 9 after the failed request and 10 after the successful request**.
- These results distinguish a failed initiation from an error response returned after an unintended commit.

## 15 November 2026 owner-directed scope amendment — local draft

The owner explicitly narrowed this follow-up to “capture payment failed if service is down, and OK once up again”, demonstrated here at the payment-initiation endpoint. This dated **local scope-amendment draft** records focused acceptance, not completion of every original #459 criterion; no remote issue body or PR description was changed by this record. No additional synthetic workload, long observer, card confirmation or webhook experiment gates this focused result.

No PAY NOW instruction was issued during this attempt: submit admission was reserved at **13:35:26.908025 SGT**, but a replacement application became RUNNING at the invalidating observation **13:35:31.844905**, before the human payment handoff. No card payment was performed for this capture. The original signed-webhook-500/redelivery/exactly-once scenario is **not demonstrated by this record** and must not be reported as tested; an earlier pending Checkout fixture is not a new checkout or payment created by the successful UP initiation.

R11 is the first documented actual fault in this follow-up; R1–R10 setup revisions, refused launches and missed deadlines are **not eleven executed faults**, and their preparation failures remain retained. The [13 November identity R1 failure](../20261113-aws-validation/identity-r1.md) is not converted into a pass; the [earlier normal paid-team attempt/refund](normal-team-success-r2.md#fixture-qualification-and-preserved-first-failure) is also preserved. Already-running optional captures are separate, not a gate or substitute for the two HTTP responses; no new load/long-observer result is claimed.

## Source provenance and independent checks

The [public manifest](publication-manifest.json) records SHA-256 values for **22 actual retained source files**, labelled F01–F22; the exact-filename map stays private. Offline validation checked request/session/actor/product bindings, proof-history timing, unchanged runtime definitions/images, all nine intermediate-milestone source hashes, and both DB objects against the actual native output. The intermediate milestone still said DB work was pending; the later completed DB evidence below supersedes that status without rewriting the original receipt.

| Source aliases | What they record |
| --- | --- |
| F01–F03 | Original fault, independent restoration request and exact-task proof history. |
| F04–F07 | Down HTTP result, Linux receipt, actual browser response and request source. |
| F08–F11 | Authenticated recovery, actual UP initiation, prior healthy runtime and actor binding. |
| F12–F15 | Completed DB reconciliation, exact read-only SQL, native exit 0 and raw DB output. |
| F16–F19 | Withheld human payment, admission reservation, restoration and outage reconciliation. |
| F20–F22 | Intermediate milestone and before/after-down state receipts. |

SQL source SHA-256: `a59278bb68c02a94561b7ecd934985a40e7dd59b588c35bba4a9d09b57e2a9c3`. The SQL uses repeatable-read/read-only transactions; its failed-request window is fault-start minus five seconds through response-recorded plus five seconds, including deleted rows. The successful-intent check is scoped to the exact returned intent. This is scoped absence/creation proof, not an account-wide absence assertion.

This document is a local evidence record. It does not assert that PR #808 is merged, that cleanup is complete, or that the original broader acceptance criteria were all executed.
