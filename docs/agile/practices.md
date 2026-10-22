# Agile engineering practices: where the evidence is

The SWE5006 module *Essential Practices for Agile Teams* covers:

- user stories;
- test-driven development (TDD);
- pair programming;
- refactoring;
- agile architecture;
- continuous integration (CI).

The practice-module project is where the team shows them. The evidence already exists in day logs, plans, ADRs and CI. This page points to it, one practice at a time, and says what the evidence does **not** show. It is source material for report sections 2 and 4 and for the deck. The course references are modules 01, 02, 03, 04, 05, 06 and 07 of that course; the course code is EPAT in the deck.

## User stories (EPAT 06)

- **Format.** Product stories are written "As a ⟨role⟩, I want ⟨goal⟩, so that ⟨benefit⟩", with owner, reviewer, estimate and issue, in each sprint plan's backlog table. An example is [Sprint 3 plan §4](sprint-03/plan.md#4-sprint-3-backlog): US23-US32, such as US24 "As a buyer, I want a My Blocs view on `/account`, so I can track open, completed and refunded blocs".
- **Acceptance criteria.** Every story issue carries a checklist, from the `use-case`, `nfr` and `task` issue templates in `.github/ISSUE_TEMPLATE/`. The sprint plans restate the acceptance criteria next to each story ([Sprint 3 plan §5](sprint-03/plan.md)).
- **Not shown.**
  - No INVEST check is recorded.
  - Several enabling stories are written for a developer or operator, for example US23 "As an operator…". EPAT 06 advises against developer-valued stories.
  - Priority and size are blank on the board items.

## Test-driven development (EPAT 03)

| Date | Evidence | What it shows |
|---|---|---|
| 2026-09-27 | [Sprint 3 log, 27 Sep](sprint-03/log/2026-09-27.md) | Four new PostgreSQL tests failed first, then passed after the implementation |
| 2026-10-01 | [Sprint 3 log, 1 Oct](sprint-03/log/2026-10-01.md) | Admin console steps: "tests written first per step", 242 focused tests passed afterwards |
| 2026-10-03 | [Sprint 3 log, 3 Oct](sprint-03/log/2026-10-03.md) | Rate limits: test-first HTTP checks failed in all three cases (expected 429, got 200) before the implementation |
| 2026-10-22 | [Sprint 4 log, 22 Oct](sprint-04/log/2026-10-22.md) | `TeamResolutionListenerOrderTest` (#611): with a listener order swapped, two of its tests fail (shown red), then pass on the real order |

**Supporting practice.**

- Unit tests for the classes with complex behaviour, for example `GroupDeal`, `PurchaseIntent`, `StripeCheckoutService`, `RefundService` and `OrderFulfilmentAdapter`.
- A race test for the last seat: `StripeLocalIntegrationTest.twentyBuyersRaceForLastSeatAndNineteenRefundObligationsPersist`.
- SonarCloud requires at least 80 % coverage on new code ([quality gates](../quality/quality-gates.md)).

**Not shown.** The order "test before code" is recorded in the day logs, not in the commit history; commits are squashed when they merge.

## Pair programming (EPAT 02)

| Date | Evidence | What it shows |
|---|---|---|
| 2026-10-14 | [Sprint 4 log, 14 Oct](sprint-04/log/2026-10-14.md), "paired with @Yorick-Yu" (#469, #468) | @l0sgAi and @Yorick-Yu paired on the identity-port refactor |
| 2026-10-14 | [Attention nicknames plan](sprint-04/attention-nicknames-plan-20261014.md), sections on scope and commits | Because the two were pairing, both owners' parts went into one PR, with every commit `Co-authored-by: Yorick-Yu` |

**Not shown.** This is the only recorded pairing session. Pairing was not a standing practice; reviews were the main way two people looked at one change.

## Refactoring (EPAT 05)

| Date | Evidence | What it shows |
|---|---|---|
| 2026-09-26 | #186, PR #187 `refactor(groupbuy): keep team completion in the domain` | Behaviour moved from a service into the aggregate |
| 2026-09-30 | [Sprint 3 log, 30 Sep](sprint-03/log/2026-09-30.md), four entries "Kind: refactor" | SonarCloud-driven clean-ups (S3776 complexity, S3358 nested ternaries, S6759 and others), with no behaviour change |
| 2026-10-07 | [Sprint 3 log, 7 Oct](sprint-03/log/2026-10-07.md); #421, #422, #423, PR #424 (merged 13 Oct) | Injected the existing Factory Method, shared one set of admission guards between quoting and paid admission, typed the receipt fingerprint |
| 2026-10-14 | [Sprint 4 log, 14 Oct](sprint-04/log/2026-10-14.md), "Kind: refactor" (#469) | Replaced a cross-context `sys_user` join with the identity port |

Each refactoring ran under the full test suite (`./mvnw verify`, counts in the entries) and the ArchUnit rules.

## Agile architecture (EPAT 04)

- **Decisions recorded when they are made.**
  - Backend: ADRs 0001-0008 in `backend/docs/adr/`, plus the locked stack decisions D1-D20 in `backend/docs/design/backend-skeleton-plan.md`.
  - Frontend: ADRs 0001-0015 in `frontend/docs/adr/`.
  - Cross-cutting decisions are dated paragraphs in the owning document (the rule in [README](README.md#the-rule)).
- **Architecture as executable rules.** The ArchUnit rules in `backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java` run with the unit tests, for example `domain_is_framework_free`, `controllers_never_touch_persistence` and `application_uses_ports_not_tech`.
- **Evolving the architecture.** Backend ADR 0008 keeps one code base that runs either as the SWE5006 monolith or as the SWE5001 services, a decision taken in Sprint 4 without a rewrite.
- **Not shown.** Some decisions have no ADR: MyBatis instead of JPA, the session-token library, scheduling and the concurrency design. The deck lists them as gaps.

## Continuous integration (EPAT 07)

- **Small, frequent merges to one mainline.** 340 pull requests merged into `dev` from 14 September to 22 October 2026, about nine a day (`gh pr list --state merged --base dev --search "merged:2026-09-14..2026-10-22"`). Sprint 3 limited PRs to about 1,500 changed lines ([Definition of Done](definition-of-done.md)).
- **Every merge is built and tested.** The required checks run on every pull request and must pass against the latest `dev`: build quality, contract, SonarCloud, Gitleaks and dependency scanning ([backend pipeline](../ci/backend-pipeline.md#required-pr-checks)).
- **The mainline deploys itself.** A merge to `dev` deploys the backend to EC2 and the frontend to S3 and CloudFront, with health checks and rollback ([backend pipeline](../ci/backend-pipeline.md#release-and-deployment-behavior)).
- **A red build is fixed first.** See [Fixing a red check](../ci/backend-pipeline.md#fixing-a-red-check) and the [21 October CI failure audit](../ci/ci-failure-audit-20261021.md).
- **Not shown.** EPAT 07 suggests integrating at least daily from short-lived branches. Some Sprint 2 branches lived for days; the Sprint 2 retrospective led to the 1,500-line limit in Sprint 3.
