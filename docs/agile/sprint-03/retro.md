# Sprint 3 retrospective (reconstructed 2026-10-24)

No retrospective meeting was recorded for Sprint 3: this file was reconstructed on 2026-10-24 from the [review](review.md), the [day logs](log.md) and the [Sprint 4 plan](../sprint-04/plan.md), and it is not minutes of a meeting. It records no quotes, votes or attendance. Actions marked "proposed, not adopted" come from this reconstruction; Sprint 4 never adopted them.

## What went well

- Most of the backlog merged and passed its tests on `dev`: 11 of 17 work items Done and 117 of 150 h burned by the freeze ([review.md:17-36](review.md); [definition-of-done.md:53](../definition-of-done.md)).
- The critical path started early: US25 team terms merged on 09-29 (PR #242), ahead of the 10-01 target ([plan.md:96](plan.md)); US29 (PR #234) and US26 (PR #235) merged on 09-28.
- Verification moved into the first half, as the Sprint 2 retrospective asked: the US23 baseline was recorded on 09-30 (PR #289, [log/2026-09-30.md:28](log/2026-09-30.md)), not on the planned 10-05 ([plan.md:100](plan.md)), though on a local stack only.
- Gate findings were fixed, not waived: SonarCloud S3776 and duplication ([log/2026-10-01.md:89](log/2026-10-01.md), [log/2026-10-05.md:61](log/2026-10-05.md)) and checkov CKV_AWS_136 ([log/2026-10-04.md:20](log/2026-10-04.md)).
- CI on `dev` ended green: 10 of 10 runs for backend, frontend and security ([review.md:47-49](review.md)).

## What could have gone better

- The goal was not met on dev. Checkout, scheduling and refunds were proven only locally (#223, #224); the operator step had no AWS CLI or agreed dev fault point ([review.md:9](review.md); [log/2026-10-03.md:28-29](log/2026-10-03.md)).
- #222 and #223 waited on each other, so #222 was split into phases and Phase B carried over ([log/2026-10-01.md:95](log/2026-10-01.md); [review.md:18](review.md)).
- The board Estimate field stayed empty all sprint, so the burndown was reconstructed at freeze again, and #232 has no Daily Scrum comments ([log/2026-10-08.md:40](log/2026-10-08.md); [review.md:13](review.md)).
- Unplanned work competed with the plan: the admin console backend asks, Redis caching and the SWE5001 pipeline ([review.md:38](review.md)), and admin console screens that plan §4 had put out of scope ([plan.md:73](plan.md); [log/2026-10-01.md:59](log/2026-10-01.md)).
- US31 and US32 never started (18 h): US31 blocked on NFR-37 (#227), US32 on #223 and #222 ([review.md:23,28](review.md)).
- A session token was pasted into a chat, as an origin secret had been in Sprint 2 ([log/2026-10-08.md:48](log/2026-10-08.md); `docs/sprint2-progress-report.typ:372`); the local demo seed reached dev ([log/2026-10-01.md:97](log/2026-10-01.md)).

## What we will try next sprint

| Action | Owner | Tracked in |
|---|---|---|
| Enter remaining hours in the board Estimate field every working day | each card owner; Sprint 4 SM @EricWang1358 checks ([sprint-04/plan.md:98](../sprint-04/plan.md)) | The one retrospective action Sprint 4 adopted ([sprint-04/plan.md:141](../sprint-04/plan.md)); DoD rule 10 ([definition-of-done.md:27](../definition-of-done.md)). Adopted but not kept daily: 47 cards carry an estimate in the 10-12 snapshot and none changed by 10-23 (`docs/agile/metrics/2026-10-12.csv`, `2026-10-23.csv`) |
| Make #223 the first job: Checkout, scheduling and refunds on dev by 10-14 | @Yorick-Yu | [sprint-04/plan.md:129](../sprint-04/plan.md) (§6 item 2) and risk row [:175](../sprint-04/plan.md). Paid START and the expiry refund shown on dev on 10-23, nine days late (PR #664; [definition-of-done.md:54](../definition-of-done.md)); #223 still open on 10-24 |
| Close blocked carry-overs instead of carrying them again | Sprint 4 Planning (all five) | Grade-driven cuts: #226 and #229 closed as not planned ([sprint-04/plan.md:62](../sprint-04/plan.md)) |
| Hold the mid-sprint owner check and record its outcome | Sprint 4 SM @EricWang1358 | Planned for 10-15 ([sprint-04/plan.md:16,184](../sprint-04/plan.md)); the Sprint 3 check of 10-02 ([plan.md:12](plan.md)) and the Sprint 4 one both have no day-log entry |
| Record each Daily Scrum as a comment on the container issue | Scrum Master | Proposed, not adopted: #465 has no comments on 10-24 |
| Never paste a token or secret into a chat; if it happens, revoke or rotate the exposed credential at once, and invalidate active sessions when it is a session token | all five | Proposed, not adopted: no Sprint 4 rule or check |
