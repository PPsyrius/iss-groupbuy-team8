# Sprint 4 retrospective (2026-10-12 to 2026-10-23)

**Reconstructed on 2026-11-06 from the record; no retrospective meeting was held.**

This file was written from the [review](review.md), the [burndown data](burndown.csv), the [plan](plan.md), the [day logs](log.md), the daily board snapshots and GitHub PR, issue and Actions history. It is not minutes of a meeting and records no quotes, votes, attendance or opinions. Every point cites its evidence. Actions without a recorded owner are marked "to be confirmed by the team".

It does not meet #619's criterion of a retrospective held with the team's input on or after 23 October. No team member has reviewed it yet; the last action below asks the team to confirm or correct it, and until then it is input for that retrospective, not its outcome.

## What went well

- The plan's priority order held. The tier-1 microservice split (#455, #456, #467, #468, #469) closed on 13–14 October, before the 16 October SWE5001 freeze ([plan.md §6 item 6](plan.md); [review.md](review.md)), and the burndown was below the ideal line on 15 October (113.5 h against 128.7 h, [burndown.csv](burndown.csv)).
- The three-service release reached `prod` before the SWE5001 presentation: promotions on 17, 19 and 20 October, the last with the smoke suite green (PRs #534, #564, #574).
- The paid journey that Sprint 3 could show only locally was shown on deployed dev: paid START and the expiry refund on 23 October, a second buyer's paid JOIN on 24 October (PR #664; #657; [log/2026-10-23.md](log/2026-10-23.md)).
- Backend and frontend CI on `dev` stayed green through the sprint: ci-backend 34 of 34 runs, ci-frontend 32 of 32 (`gh run list --branch dev --created 2026-10-12..2026-10-23 --limit 1000`). The security workflow failed 2 of 123 runs, both the scheduled full-history scan ([review.md](review.md) "Environment and CI").
- The Sprint 3 action "close blocked carry-overs" was kept: #226 and #229 were closed as not planned before planning ([plan.md §4 item 8](plan.md)).
- Deck feedback ran through issues: ten sub-issues of #605 were filed and closed before the 25 October rehearsal (nine by PR #671).

## What could have gone better

- Cards were not closed when their work was done. Fifteen of 28 issue-backed items were open on 23 October and the burndown stayed at 96.5 h from 19 October, although the SWE5001 presentation (#461) ran on 21 October and the smoke suite (#460) passed on 20 October ([burndown.csv](burndown.csv); [review.md](review.md)).
- #619, which tracked this review and retrospective, was closed by accident on 26 October because the body of PR #683 said "Open item 21 closed: #619", and nobody noticed until 6 November ([#619 comment](https://github.com/PPsyrius/iss-groupbuy-team8/issues/619)).
- Checkout, scheduling and refunds reached dev on 23 October, nine days after the 14 October target ([plan.md §6 item 2](plan.md); [log/2026-10-23.md](log/2026-10-23.md)).
- The 19 October AWS evidence session captured one HTTP scaling run, which did not meet its acceptance check; queue-driven scaling, self-healing, the identity outage and the DLQ drill were not captured on AWS, and the presentation demo was cut to three steps ([log/2026-10-19.md](log/2026-10-19.md); #458, #459, #448 open on 6 November).
- The 15 October mid-sprint check and identity go/no-go have no record in the day logs or on #465 ([plan.md §1](plan.md)); neither do a Sprint Review or retrospective.
- Two of five use-case and design-problem drafts were in the repository by 23 October (PRs #486, #542), against the plan's 23 October due date ([plan.md §11](plan.md)).
- The 25 October timed rehearsal ran 47 minutes for 34 minutes of slots (1.38 times), one day before the talk (body of PR #683).
- SWE5001 progress report 2 (#462, due 18 October) has no record in the repository.

## After the sprint

- 29–30 October: 17 Actions runs failed, 16 of them on Dependabot pull-request branches (8 on the two TanStack group branches) and one on the `v0.1.0-swe5001_report` tag push; none on `dev` (`gh run list --created 2026-10-29..2026-10-30 --limit 1000`). This is outside the sprint window and is recorded here only as input for the team.

## Follow-up of the Sprint 3 actions

| Sprint 3 action | What happened in Sprint 4 | Evidence |
|---|---|---|
| Log hours daily: enter remaining hours in the board Estimate field every working day | Not kept. Adopted as plan §7 and DoD rule 10, but no member logged hours, and none of the 47 estimates set by 12 October changed in any snapshot up to 27 October; the sum over the Sprint 4 issues stays at 224.5 h every day. On 19 October all five members agreed an effort split in person-days instead, an estimate rather than a measurement | [burndown.csv](burndown.csv) `board_estimate_h`; `docs/agile/metrics/2026-10-12.csv` to `2026-10-27.csv`; [log/2026-10-19.md](log/2026-10-19.md); [definition-of-done.md](../definition-of-done.md) |
| Make #223 the first job: Checkout on dev by 14 October | Done nine days late, on 23 October | [log/2026-10-23.md](log/2026-10-23.md); PR #664 |
| Close blocked carry-overs instead of carrying them again | Kept: #226 and #229 closed as not planned on 11 October | [plan.md §4 item 8](plan.md) |
| Hold the mid-sprint owner check and record its outcome | Not recorded | No day-log entry for 15 October mentions it |
| Record each Daily Scrum on the container issue (proposed, not adopted) | Not done | #465 has no Daily Scrum comments |
| Never paste a secret into a chat (proposed, not adopted) | No incident recorded in the Sprint 4 day logs; no rule or check added | [log.md](log.md) |

## Actions

Sprint 4 was the last sprint, so no later sprint can test these actions.

| Action | Owner | Basis |
|---|---|---|
| Close each open Sprint 4 issue whose work is delivered, or split off what remains: #227, #443, #454 (work done), #460, #461, #464 (delivered) | The planned card owner (plan §5); to be confirmed by the team | [review.md](review.md) "Carried over" |
| Decide whether #224, #448, #458 and #459 get their AWS or record evidence before the SWE5001 submission on 16 November, or are closed as known limitations | Planned owners @Yorick-Yu (#224, #459), @l0sgAi (#448), @EricWang1358 (#458); to be confirmed by the team | [log/2026-11-05.md](log/2026-11-05.md) says the R0 evidence session needs an owner in the 11–16 November window |
| Close a card, or split off what remains, on the day its evidence is committed | To be confirmed by the team | Final report Table 2.10, Sprint 4 row |
| Write closing keywords (`Closes`, `Closed`) in PR bodies only for issues the PR finishes | To be confirmed by the team | PR #683 closed #619 by accident |
| Confirm or correct this reconstruction | To be confirmed by the team | #619 acceptance criterion "with the team's input" |
