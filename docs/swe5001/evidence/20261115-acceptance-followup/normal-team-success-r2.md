# #460 — qualified normal team-success follow-up (R2)

**Qualified normal-path pass — not whole-issue closure.** Finalized 15 November 2026 for PR #808 alongside the focused #448 rollback test. Backend reconciliation and independent parent API checks were verified offline against retained bytes; this is not fresh CI for the publication commit. No #459 identity-outage pass is claimed.

## Scope and aliases

A and B are distinct authenticated buyers; T is their fresh successful team; E is its reconciled production TeamSucceeded event. All observation times below are UTC on **14 November 2026**; later observations fall on 15 November in Singapore. Identity/payment/team identifiers, actor profile fields, traces, provider URLs and private paths are omitted. The released application is **3a893036d70d028b29ae13ebf820283571c1ae00**, with execution-source binding **479d3409ba6992a7991953ae3544f559ca614dea**; publication does not promote an image.

This is a **normal paid START/JOIN path**, not an identity-outage experiment. The #459 identity fault never ran in this follow-up; no outage, signed-webhook-during-outage, recovery-time or redelivery/replay pass is claimed.

## Authenticated cross-service behavior

- A fresh Google login was completed in the parent's connected browser. The retained post-flow `/api/v1/auth/me` observation at **14:43:37.739** returned 200 / `COMMON_200_000`; its receipt explicitly does **not** verify the exact fresh-flow completion timestamp (H12).
- At **15:17:48.422–15:17:50.123**, the same A session received 200 / `COMMON_200_000` from identity `/auth/me`, protected commerce `/admin/products?page=1&size=1`, commerce `/payments/capabilities` with `enabled=true`, and recipient-scoped `/notifications?page=1&size=100` (H01).
- Qualification: no existing administrator was available to the parent, so a canonical exact-actor role grant preceded these checks. Private mappings show A's admin membership count **0 before / 1 after**, at **15:03:24.521275 / 15:05:50.793999** (H10–H11). This proves protected access **after provisioning**, not the original “existing admin without a grant” condition; no waiver or owner acceptance is inferred.
- OAuth limit: no complete redirect-chain/network capture or direct Valkey state-key creation/consumption observation is retained here. Successful fresh flow plus authenticated requests supports browser acceptance, not a standalone live Valkey-state experiment. No cookie/session value was extracted for these recipient API checks.

## Fixture qualification and preserved first failure

- Only the dedicated fixture's **future-team lifetime** changed from **2400 to 7200 seconds**, with expected product version **3**, returning version **4**, HTTP 200 / `COMMON_200_000` at **16:47:38.593**. Target size remained **2**, existing policy price **22.00 SGD**; the request contained **no policy update** (H08).
- This is a **two-hour fixture qualification**, not an unchanged forty-minute-fixture pass. The earlier team's stored deadline was not extended or rewritten.
- Preserve the original failure: A's paid START was fulfilled, but B's JOIN checkout returned **409 at 16:37:21.703** with only **174.656754 seconds** of deadline headroom, below the unchanged **1860-second** requirement. It was **not expired at rejection**; the team subsequently expired. No B payment, provider-session binding or membership was persisted by that rejected path.
- The retained diagnosis and final reconciliation show the earlier team expired at **1/2**, its original payment succeeded/fulfilled, its normal expiry refund succeeded for **22.00 SGD**, and its TeamSucceeded outbox count remained **0**. This preserves orchestration-failure/refund evidence, not team-success or exhaustive provider-session absence proof (H09, H13).

## Successful R2 browser/API observations

| Observation | UTC | Actual assertion |
| --- | --- | --- |
| A START settlement | 16:49:22.539–16:49:22.580 | Payment `SUCCEEDED`, outcome `FULFILLED`; T open at **1/2** (H02). |
| B JOIN settlement | 16:52:01.782–16:52:01.807 | Payment `SUCCEEDED`, outcome `FULFILLED`; same T succeeded at **2/2**, success time **16:51:14.940948** (H03). |
| B notification list / repeat | 16:52:14.976 / 16:53:48.928 | All **2** recipient rows returned each time; exactly **1** TeamSucceeded referencing T (H06–H07). |
| A notification list / repeat | 16:59:57.192 / 17:00:10.219 | All **4** recipient rows returned each time; exactly **1** TeamSucceeded referencing T (H04–H05). |

All four notification reads returned 200 / `COMMON_200_000`; each enumerated the complete recipient list in one page of size 100, matching item count to total. Counts were independently recomputed from type/reference, not merely summary flags. Both buyers' title was **“Your team is complete”** and content **“Your group-buy team reached its target size of 2 members.”** Repeats retained the same notification IDs and exact-one result; these are stable reads, **not another broker replay/duplicate-delivery experiment**.

## Verified backend source/event/effect reconciliation

The native read-only reconciliation observed source/effects at **17:04:14.501916**, with acceptance recorded **17:10:01.821306** (H13–H14): **2** succeeded/fulfilled payments, **2** fulfilled intents, **2** applied participation receipts, **2** paid members, **2** confirmed orders and **2** successful webhook receipts; **0** R2 refunds. T succeeded at **2/2**. Exactly **1** TeamSucceeded E was published with exact payload recipients `{A,B}`, no failed publication, **1** notification per recipient, **0** extra/deleted recipient rows and **1** notification consumed-event marker. Initial/repeat API IDs for each buyer match their respective DB row for E. Two PaymentSucceeded events were also published; they do not substitute for E.

Native task exit **0** and the exact completion marker preceded a local multiline-JSON parser failure; the same raw output was decoded/reconciled offline, with **no task replay or business mutation**. Publication review independently checked raw source/event/effect rows, all **50** source-manifest artifact hashes, and complete-page API-to-DB bindings. Parent's independent API reconciliation at **17:19:22.155115** separately passed distinct actors/payments, same succeeded team, complete paging, distinct notification IDs and stable repeats; that API-only receipt does not itself claim backend/identity-fault acceptance (H15).

## Reused topology evidence and acceptance disposition

- Reuse audited 11 November released-application smoke: monolith run **42955188339** / job **130308501094**, distributed run **42966614196** / job **130345191733**, release **3a893036**; both passed the same **nine final assertions**. Retain monolith's initial **503 followed by retry success**; no unsupported root cause is assigned. Neither topology smoke was rerun.
- Older anonymous smokes plus new distributed manual checks are **cross-date evidence**, not one calendar session or full authenticated EC2-versus-ECS parity. No continuous-runtime linkage is asserted here.
- Supported: qualified distributed authenticated routing/admin access after grant; real paid T reaching 2/2; reconciled production E through publication/consumer effects; exact-one API-readable TeamSucceeded per buyer on complete initial/repeat lists.
- Still required for whole-issue closure: owner/reviewer disposition of session reuse, admin provisioning versus “existing admin”, two-hour fixture, preserved failure, incomplete OAuth/Valkey observation and missing EC2 manual comparison. Original wording is not treated as waived; no automatic #460 closure or #459 pass. No teardown/archive acceptance is established by these business receipts.

## Selected raw-artifact provenance

[Publication manifest](publication-manifest.json) binds this sanitized report. Hashes below identify private source bytes, not independent acceptance or public availability; the alias-to-original-file map remains private. Submitted reports, SWE5006 material and #663 are unchanged.

| Alias / observation | SHA-256 |
| --- | --- |
| H01 cross-service | `c6d78e7311fa7d473124867fec1e02b50afc3d65448211a0f74d69a41b540cc6` |
| H02 START | `9d379d33b7172e7d1d5280216b79f078ea53a37b670e35fcff667c0f89edee41` |
| H03 JOIN | `5bc536fd80fb820d469f8667b1ebdc97410aad0e92162c143f0a2e873c430e10` |
| H04 A notifications | `45c8e47b4977e64c44cf2439feb8649faf5f7846e61b9b0e31c6d5659d545778` |
| H05 A repeat | `e2763c40c2f8dfffb8583f7d1c6ba1fecaf025be035650cfb4a639a54feebb0f` |
| H06 B notifications | `943b1db5ca4b3fbe756bf7bfedf44831911e206b9fffe5ac38390c34381e2201` |
| H07 B repeat | `6140f176349c5a4277a292de094d9b383e792d1e461a7f01d5663450c9cc3398` |
| H08 fixture lifetime | `d8830ca163e9c3626e000edb718c35c9e8e06dacb8c11f2a546ed255c317b29d` |
| H09 preserved failure | `0782176b02ae177b0e2c2b43b40676a0c0af56610e930c0c0543fbe0f51fc95a` |
| H10 admin mapping before | `b71742c7bd82029c382284eb4eb1db23d16f15b2fff06e368fe202c7141e8682` |
| H11 admin mapping after | `fe8cfabae67c17a39cf649a0594312ba6d052655e6f0d4cf436192c603737467` |
| H12 post-OAuth actor check | `bac173589cdf105830fa22d6f6ae16dc1db8e69120b3f49cebda0e25515688c9` |
| H13 backend acceptance | `f64c59cbe41948252df9996252a6527152ccd55d06dff3316e61184082ba5d79` |
| H14 backend source manifest | `388a054d2a36407fbb3f5c812db56f6adddf490a1bb1bec614c370138aeddfa5` |
| H15 independent parent API | `cda75ebe0059f573dff5ee8da4e7ae97c6dc3229fc7c5ecb43007cd70efb97bf` |
