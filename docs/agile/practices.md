# Agile engineering practices: where the evidence is

This page points to the repository's evidence for the practices of *Essential Practices for Agile Teams* (EPAT; module and PDF page in brackets), and says what the evidence does **not** show. Each row quotes what a day-log entry records; the entry is the source. Checked on `dev` at `14996412`.

## User stories (EPAT 06)

- **Format.** The Sprint 3 backlog writes stories "As a ⟨role⟩, I want ⟨goal⟩, so that ⟨benefit⟩" with owner, estimate and issue ([Sprint 3 plan §4](sprint-03/plan.md#4-sprint-3-backlog)), for example US24 "As a buyer, I want a My Blocs view on `/account`, so I can track open, completed and refunded blocs". The Sprint 4 backlog lists tasks, not stories.
- **Acceptance criteria.** The Sprint 3 plan states them per story (§5); the `use-case`, `nfr` and `task` issue templates carry a checklist.
- **Not shown.** No INVEST check is recorded (06 p. 19). Some stories have no benefit clause (US13, US23, US27, US30), and some are written for an operator or developer, which 06 p. 12 advises against. Priority and size are blank on the board.

## Test-driven development (EPAT 03)

The course's cycle is test first, red, green, refactor (03 pp. 13, 32).

| Entry | What it records |
|---|---|
| [27 Sep, #205](sprint-03/log/2026-09-27.md) | "Four new PostgreSQL tests first failed", then passed with the bounded offer query |
| [1 Oct, #326 to #329](sprint-03/log/2026-10-01.md) | Four admin-console changes: "new tests failed first", then passed |
| [3 Oct, #274](sprint-03/log/2026-10-03.md) | Rate limits: "test-first HTTP checks failed in all three cases with expected 429 versus actual 200 before implementation" |
| [7 Oct, #421 to #423](sprint-03/log/2026-10-07.md) | "Two zero/negative-count red tests reproduced the quote defect before the fix" |

**Not shown.** The red step is recorded in day logs, not in the history: commits are squashed when they merge. No entry records the refactor step of the cycle.

## Pair programming (EPAT 02)

| Entry | What it records |
|---|---|
| [14 Oct, #469, PR #490](sprint-04/log/2026-10-14.md) | @Yorick-Yu "paired with @l0sgAi" on one branch, "one PR for both halves", and @l0sgAi "paired with @Yorick-Yu in the same PR" |

**Not shown.** The entries do not say how the pair worked: one computer with a driver and an observer who switch roles (02 p. 4) is not recorded. This is the only recorded pairing.

## Refactoring (EPAT 05)

Refactoring changes structure without changing external behaviour (05 p. 9).

| Entry | What it records |
|---|---|
| [30 Sep, #296](sprint-03/log/2026-09-30.md) | `ProductDetailPage` (cognitive complexity 90) split into hooks, components and pure rules in `listing-view.ts`; "no visual, behaviour or contract change"; existing route and `DealProgress` tests pass unchanged, with 17 new tests |
| [14 Oct, #469, PR #490](sprint-04/log/2026-10-14.md) | Payment attention reads nicknames through the identity port instead of joining `sys_user`. `AdminPaymentFlowIT` cases "were committed first and passed on the old SQL (22/22), then stayed green after the refactor (23/23 with the new disabled-buyer case)" |

**Not shown.** #469 also changed one behaviour on purpose (a disabled buyer now reads "Unknown buyer"), so it is a refactoring plus a change. The 30 Sep entries #295, #297 and #298 are SonarCloud rule fixes rather than smell-driven refactorings.

## Agile architecture (EPAT 04)

- **Decisions written down as they are made**: backend ADRs 0001-0008, the backend stack decisions D1-D20 in `backend/docs/design/backend-skeleton-plan.md`, frontend ADRs 0001-0015, and dated paragraphs for cross-cutting decisions ([the rule](README.md#the-rule)).
- **Architecture as executable rules**: the ArchUnit rules in `backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java` (for example `domain_is_framework_free`, `controllers_never_touch_persistence`, `application_uses_ports_not_tech`) fail `./mvnw verify` on a violation.
- **Evolution across sprints**: the backend ADRs are dated from 22 September to 12 October; ADR 0008 lets one code base run as the SWE5006 monolith or as the SWE5001 services.
- **Not shown.** ADR 0007 is still "Proposed". MyBatis and the session-token library are D-table rows, not numbered ADRs.

## Continuous integration (EPAT 07)

- **One mainline, frequent merges.** 342 pull requests were merged into `dev` from 14 September to 22 October 2026, 73 of them by bots (`gh pr list --state merged --base dev --search "merged:2026-09-14..2026-10-22" --limit 1000`, counted on 22 October). Sprint 3 limited a PR to about 1,500 changed lines ([Sprint 3 plan §8](sprint-03/plan.md#8-definition-of-done)).
- **Merges are checked.** The `dev` ruleset requires six checks, `build-quality`, `SonarCloud Backend`, `docker`, `gitleaks`, `frontend-check` and `SonarCloud Frontend`, which skip when a PR does not touch their part ([required PR checks](../ci/backend-pipeline.md#required-pr-checks)); CONTRIBUTING: "Red pipelines don't merge".
- **Dev deploys from the mainline.** A merge to `dev` deploys the backend to EC2 when `ENABLE_EC2_DEPLOY` is set, with a health check and automatic rollback where a previous image exists ([release and deployment](../ci/backend-pipeline.md#release-and-deployment-behavior)), and the frontend to S3 and CloudFront when `ENABLE_S3_DEPLOY` is set (`ci-frontend.yml`). SWE5006 has no production environment.
- **Not shown.** 07 p. 33 asks to fix or revert a broken build within about ten minutes; the weekly full-history Gitleaks scan has failed every week since 21 September ([CI failure audit](../ci/ci-failure-audit-20261021.md)). 07 p. 41 asks to check in to trunk at least once a day; the team works on feature branches kept up to date with `dev` instead.
