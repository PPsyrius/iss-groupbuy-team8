# Definition of Done

The Product Owner and the team agree what "done" means before work is counted (Essential Practices for Agile Teams, 01 Agile pp. 25-26). Until 22 October 2026 the team's DoD existed only as copy-forward deltas:

- the Sprint 2 progress report (`docs/sprint2-progress-report.typ`, "Definition of Done");
- [Sprint 3 plan §8](sprint-03/plan.md#8-definition-of-done) ("the Sprint 2 DoD carries forward, with three changes");
- [Sprint 4 plan §8](sprint-04/plan.md#8-definition-of-done) ("the Sprint 3 DoD carries forward, with these additions").

This page merges them into one list. It changes no rule: a change to the DoD is a team decision taken at a retrospective, recorded in the next sprint plan and then here. No written Sprint 1 DoD was found. The Sprint 2 report says its list was "carried forward from Sprint 1 with three additions" but does not say which.

## Current Definition of Done (Sprint 4)

A story or task is done when all of the following hold.

| # | Rule | Since | How it is checked |
|---|---|---|---|
| 1 | Acceptance criteria are satisfied and linked to the story or issue. | Sprint 2 | Reviewer, against the issue's acceptance-criteria checklist |
| 2 | The endpoint is in `contracts/openapi.json` **before** its controller PR is opened, and the contract-drift check passes without a waiver. | Sprint 2; "before" from Sprint 3 | `contract-check` required check; `contract-change-approved` label is the only waiver |
| 3 | The implementation respects the COLA and DDD boundaries. | Sprint 2 | ArchUnit rules in `ArchitectureTest`, run with the unit tests |
| 4 | Tests cover the main path, validation failures, and the replay and concurrency cases of the story. | Sprint 2 | Review; SonarCloud ≥ 80 % coverage on new code |
| 5 | Every cross-domain reaction states its transaction phase (inside or after the publisher's transaction), and a test proves it. | Sprint 2 | Review; for example `TeamResolutionListenerOrderTest` |
| 6 | Maven verify, Spotless, Checkstyle, SpotBugs, ArchUnit, Gitleaks, Trivy, both SonarCloud gates and `pnpm check` pass. | Sprint 2; both SonarCloud gates and `pnpm check` from Sprint 3 | Required checks ([backend pipeline](../ci/backend-pipeline.md#required-pr-checks), [quality gates](../quality/quality-gates.md)) |
| 7 | The change is reviewed, and contains no secret, no unresolved blocking defect and no unapproved API break. | Sprint 2 | `dev` ruleset requires an approving review; Gitleaks; `openapi-diff --fail-on-incompatible` |
| 8 | Where the story says "on dev", it is demonstrated on the deployed dev environment, with the evidence in the day log. A local run does not count. | Sprint 3 | Day-log entry with the dev evidence; sprint review |
| 9 | The PR has fewer than about 1,500 changed lines, or is one layer of a stacked series. | Sprint 3 | Review |
| 10 | Remaining hours are in the board's estimate field on the day the card moves. | Sprint 3 | Daily board snapshot in `docs/agile/metrics/` |
| 11 | The board, the day log, the NFR register (where applicable) and the contribution record are updated before completion is counted. | Sprint 2; day log and NFR register from Sprint 3 | PR template checkboxes; review |
| 12 | SWE5001 changes are additive: `topology=monolith` stays the default, the SWE5006 required checks stay required, and the EC2 deploy is confirmed healthy after any merge that touches the bootstrap jar, Dockerfiles or Compose files. | Sprint 4 | Required checks; `deploy-existing-ec2` health check |
| 13 | "On ECS" evidence is captured in a recorded session (workflow run link, k6 summary, dashboard screenshot) and linked from the day log. An ElasticMQ test does not prove the SNS hop. | Sprint 4 | Day-log entry |
| 14 | Every ECS session ends with `env-down.sh` and a clean `env-status.sh`, recorded in the day log. | Sprint 4 | Day-log entry |
| 15 | A design choice made while building is drafted into the owner's #463 use-case or design-problem section in the same week. | Sprint 4 | Report drafts under `docs/swe5006/report-drafts/` |

**Superseded rule.** The Sprint 2 list also said: "where deployment applies, the health check passes and rollback remains available". From Sprint 3 it is covered by rule 8 and by the deploy job itself, which checks health and rolls back automatically ([backend pipeline](../ci/backend-pipeline.md#release-and-deployment-behavior)).

**Known adherence gaps.**

- The Sprint 2 report records that contracts were regenerated in the controller's PR rather than a day ahead.
- It also records that remaining effort was kept as a work log rather than in hours, so the Sprint 2 burndown was reconstructed.
- In Sprint 4, the board snapshots from 12 to 22 October show the estimates of #451, #463 and #465 never decreasing (rule 10). This is to be discussed at the Sprint 4 retrospective (#619).

## Done ladder per sprint

The course's ladder runs coded → tested → integrated → releasable → released (01 Agile p. 26). The team reads the steps as follows:

- **integrated:** merged to `dev` with all required checks green;
- **releasable:** demonstrated on the deployed dev environment (rule 8);
- **released:** used by real users. There are none, because Stripe runs in test mode.

Placing each sprint on the ladder is the team's reading of the evidence, not a course rating.

| Sprint | Dates | Goal issue | Step reached | Evidence |
|---|---|---|---|---|
| 1 | 2-12 Sep | #49 | not placed | Backend pipeline live on dev on 5 Sep with health check and rollback; frontend not started (Sprint 1 report) |
| 2 | 14-25 Sep | #93 | integrated | 9 of 11 stories, 134 of 150 h; frontend live on 22 Sep; Checkout and scheduling off on dev (Sprint 2 report) |
| 3 | 28 Sep-9 Oct | #232 | integrated | 117 of 150 h; the review records the goal as not met on dev ([review](sprint-03/review.md)) |
| 4 | 12-23 Oct | #465 | to be recorded at the 23 Oct review (#619) | |
