# Definition of Done

The course asks the team to agree what "done" means (Essential Practices for Agile Teams, 01 Agile p. 25) and gives a ladder of steps from coded to released (p. 26). The team's Definition of Done was written down sprint by sprint, each time as changes to the previous sprint's list:

- Sprint 1: the done criteria of the backend skeleton (`backend/docs/design/backend-skeleton-plan.md` §11);
- Sprint 2: the progress report (`docs/sprint2-progress-report.typ`, "Definition of Done"), "carried forward from Sprint 1 with three additions";
- Sprint 3: [plan §8](sprint-03/plan.md#8-definition-of-done), with three changes that answer the Sprint 2 retrospective;
- Sprint 4: [plan §8](sprint-04/plan.md#8-definition-of-done), with additions for the two-course sprint.

This page puts the current list in one place. The sprint documents remain the record: where the wording differs, they win. The "Since" column is read from their text. Other lists named "Definition of Done" cover something else: the SWE5001 report's sections (`docs/swe5001/report/AGENTS.md`) and coding guidance for the backend (`.claude/skills/backend-constraints-skill`).

## Current Definition of Done (Sprint 4)

A story or task is done when all of the following hold.

| # | Rule | Since | How it is checked |
|---|---|---|---|
| 1 | Acceptance criteria are satisfied and linked to the story or issue. | Sprint 2 | Review, against the issue's acceptance criteria |
| 2 | The endpoint is in `contracts/openapi.json` **before** its controller PR is opened, and the contract-drift check passes without a waiver. | Sprint 2; "before" from Sprint 3 | `contract-check` job: not a required check, but CONTRIBUTING asks for it green before approval. The `contract-change-approved` label only skips its incompatible-change step, not the drift check |
| 3 | The implementation respects the COLA and DDD boundaries. | Sprint 2 | ArchUnit rules in `ArchitectureTest`, run by `./mvnw verify` |
| 4 | Tests cover the main path, validation failures, and the replay and concurrency cases of the story. | Sprint 2 | Review; SonarCloud requires 80 % coverage on new code |
| 5 | Every cross-domain reaction states its transaction phase (inside or after the publisher's transaction), and a test proves it. | Sprint 2 | Review |
| 6 | Maven verify, Spotless, Checkstyle, SpotBugs, ArchUnit, Gitleaks, Trivy, both SonarCloud gates and `pnpm check` pass. | Sprint 2; both SonarCloud gates and `pnpm check` from Sprint 3 | The [required checks](../ci/backend-pipeline.md#required-pr-checks) plus the applicable `security.yml` jobs; gate thresholds in [quality gates](../quality/quality-gates.md) |
| 7 | The change is reviewed, and contains no secret, no unresolved blocking defect and no unapproved API break. | Sprint 2 | CODEOWNERS review (CONTRIBUTING); the `dev` ruleset requires one approval, every review thread resolved and a branch up to date with `dev` (strict required checks), and merges by squash only; Gitleaks; `openapi-diff --fail-on-incompatible` |
| 8 | Where the story says "on dev", it is demonstrated on the deployed dev environment, with the evidence in the day log. A local run does not count. | Sprint 3 | Day-log entry with the dev evidence; sprint review |
| 9 | The PR has fewer than about 1,500 changed lines, or is one layer of a stacked series. | Sprint 3 | Review |
| 10 | Remaining hours are in the board's estimate field on the day the card moves. | Sprint 3 | Daily board snapshot in `docs/agile/metrics/` |
| 11 | The board, the day log, the NFR register (where applicable) and the contribution record are updated before completion is counted. | Sprint 2; day log and NFR register from Sprint 3 | The PR template asks for the day-log entry and the NFR row; review |
| 12 | SWE5001 changes are additive: `topology=monolith` stays the default, the SWE5006 required checks stay required, and the EC2 deploy is confirmed healthy after any merge that touches the bootstrap jar, Dockerfiles or Compose files. | Sprint 4 | Required checks; `deploy-existing-ec2` health check |
| 13 | "On ECS" evidence is captured in a recorded session (workflow run link, k6 summary, dashboard screenshot) and linked from the day log. An ElasticMQ test does not prove the SNS hop. | Sprint 4 | Day-log entry |
| 14 | Every ECS session ends with `env-down.sh` and a clean `env-status.sh`, recorded in the day log. | Sprint 4 | Day-log entry |
| 15 | A design choice made while building is drafted into the owner's #463 use-case or design-problem section in the same week. | Sprint 4 | Report drafts under `docs/swe5006/report-drafts/` |

**Changes between the sprint lists**, as their text shows them. The sprint documents give no reason beyond "answer the Sprint 2 retrospective".

- Sprint 2 to 3: "before" added to rule 2; both SonarCloud gates and `pnpm check` replace "the SonarQube quality gate"; rules 8, 9 and 10 are new; rule 11 names the day log and NFR register instead of the remaining-effort record and the report. Two Sprint 2 items are not in the Sprint 3 list, with no recorded decision: "implementation matches the approved contract" and "where deployment applies, the health check passes and rollback remains available". The deploy job still checks health and rolls back automatically where a previous image exists ([backend pipeline](../ci/backend-pipeline.md#release-and-deployment-behavior)).
- Sprint 3 to 4: rules 12 to 15 are added; the rest carries forward.

**Adherence recorded so far.** The Sprint 2 report records two items applied more loosely than written: contracts were regenerated in the controller's PR rather than a day ahead, and remaining effort was kept as a work log rather than in hours, so the Sprint 2 burndown was reconstructed. Sprint 3 and 4 adherence is for their reviews and retrospectives.

## Done ladder per sprint

The course's ladder runs coded → tested → integrated → releasable → released (01 Agile p. 26). The team reads the upper steps as follows, the same basis as the deck:

- **integrated:** merged to `dev` with every required check green;
- **releasable:** shown working on the deployed dev environment (rule 8). This is lower than the course's "production ready" (p. 25);
- **released:** promoted to production through the gated release path and verified there by the smoke suite. The ladder's wording asks for a release, not for adoption by outside users, so the earlier gloss "used by real users" is dropped. Production is the shared SWE5001 release; the separate SWE5006 `backend-prod` path has never run (#226). SWE5001 production on ECS is brought up per session from released digests; its three-service release was first promoted on 17 Oct (PR #534) and last on 20 Oct (PR #574). Stripe runs in test mode there, and no outside users use it. The team agreed this reading on 24 Oct (#619).

| Sprint | Dates | Goal issue | Step reached | Evidence |
|---|---|---|---|---|
| 1 | 1-12 Sep | #49 | integrated | Backend on dev from 5 Sep; no frontend, so not releasable |
| 2 | 15-26 Sep | #93 | integrated | 9 of 11 stories merged and tested, 134 of 150 h; Checkout and scheduling off on dev (Sprint 2 report) |
| 3 | 28 Sep-9 Oct | #232 | integrated | Merged and tested; the goal was not met on dev under rule 8; 117 of 150 h, reconstructed at the freeze ([review](sprint-03/review.md)) |
| 4 | 12-23 Oct | #465 | released (shared SWE5001 production); shown on dev | SWE5001 three-service production on ECS promoted by PR #534 (17 Oct) and, last, PR #574 (20 Oct, run 39435280952, smoke suite green); paid START and the expiry refund shown on deployed dev on 23 Oct (PR #664, merged 24 Oct, bef39b72); a second buyer's paid JOIN shown on dev on 24 Oct (system demo backup recording, #657); #590 closed on it by Yorik on 25 Oct. Placed from the evidence; the team agreed the placement on 24 Oct (#619) |

Dates follow the progress reports where they exist. The placements match the deck's Definition of Done slide; they are the team's reading of the evidence, not a course rating.
