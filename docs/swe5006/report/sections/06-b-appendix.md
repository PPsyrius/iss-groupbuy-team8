<!-- report
rubric: B
owner: Liu Jin, Lim Jiechao
plan: #681
sources: backend/docs/adr/README.md; frontend/docs/adr/README.md; backend/docs/design/backend-skeleton-plan.md
owns: R26
status: draft
-->

# Appendix B: ADR Index and Decisions Without an ADR {.appendix}

The team records decisions in three places: 8 backend architecture decision records (ADRs) in `backend/docs/adr/`, 15 frontend ADRs in `frontend/docs/adr/`, and the 20 locked decisions of the backend skeleton plan (`backend/docs/design/backend-skeleton-plan.md`), agreed on 30 August before the first ADR existed. All ADRs use the Nygard format. Backend and frontend numbers are independent, so the report always says "backend ADR 0001" or "frontend ADR 0001". Section B.4 lists the significant decisions that have no ADR. Statuses and dates below were read on 26 October 2026.

#### B.1 Backend ADRs

Table B.1: Backend ADRs.

| # | Decision | Status | Date |
| --- | --- | --- | --- |
| 0001 | Purchase intent lives in the order context; team terms live on the product | Accepted | 22 Sep 2026 |
| 0002 | A team's resolution is published as an event, and its consequences run before the commit | Accepted | 22 Sep 2026; addendum 23 Sep |
| 0003 | Referral credit is a subscriber that runs after the commit | Accepted | 25 Sep 2026 |
| 0004 | Retire the unused legacy PaymentIntent path | Accepted | 28 Sep 2026 |
| 0005 | Gate Stripe Checkout with a deployment property | Accepted | 30 Sep 2026 |
| 0006 | The payment context owns the admin read model of payments that need attention | Accepted | 2 Oct 2026 |
| 0007 | Cross-service events go through a transactional outbox, one SNS topic and one SQS queue per consumer | Proposed | 11 Oct 2026 |
| 0008 | One codebase, two deployment topologies: the monolith on `dev` and three services on ECS | Accepted | 12 Oct 2026 |

Backend ADR 0007 still reads "Proposed". Its text says it becomes Accepted after team agreement at a stand-up, and no such agreement has been recorded in the file. Its implementation is nevertheless merged: the outbox writer, relay and consumer are in the code (off on dev, section 1.4). The status is therefore out of date rather than a sign that the design is unbuilt.

Backend ADR 0002's addendum of 23 September records the scheduling design for the team-expiry sweep, and its later notes add the refund worker on the same runner. Section 3.2.7 describes the jobs as built (Table 3.11).

#### B.2 Frontend ADRs

Table B.2: Frontend ADRs. All 15 are Accepted; the date is the acceptance date, and later addenda are noted.

| # | Decision | Date |
| --- | --- | --- |
| 0001 | Frontend stack (React, TanStack Router and Query) and pure client-side rendering | 17 Sep 2026; addendum 23 Sep |
| 0002 | Same-origin `/api` with a cookie session and the OAuth return path | 17 Sep 2026; addendum 27 Sep |
| 0003 | The API client is generated at build time and not committed | 17 Sep 2026; note 29 Sep |
| 0004 | One build artifact promoted from `dev` to production, with no runtime environment file | 17 Sep 2026 |
| 0005 | SPA fallback through a CloudFront Function | 17 Sep 2026; function imported 25 Sep |
| 0006 | Lint and format toolchain (oxlint, oxfmt) | 17 Sep 2026 |
| 0007 | Hand-managed `dev` CloudFront first, Terraform import in Sprint 2 | 17 Sep 2026; import merged 25 Sep |
| 0008 | Documentation and evidence conventions | 16 Sep 2026; addendum 27 Sep |
| 0009 | `Result<T>` envelope handling through one `unwrapResult` function | 16 Sep 2026 |
| 0010 | React Compiler through the Babel preset now, the native path as a spike | 17 Sep 2026; addendum 23 Sep |
| 0011 | Testing strategy: Vitest, MSW and a Playwright smoke test | 17 Sep 2026 |
| 0012 | Configuration for coding agents | 17 Sep 2026 |
| 0013 | Package manager and runtime pinning (pnpm, Node) | 17 Sep 2026 |
| 0014 | Payments through Stripe Hosted Checkout with a server-owned purchase intent | 23 Sep 2026; addendum 24 Sep |
| 0015 | Admin console: second shell, roles guard, pessimistic versioned writes | 1 Oct 2026 |

#### B.3 Locked decisions of the backend skeleton plan

The skeleton plan fixed the backend's stack and structure on 30 August, before any ADR was written. Three of its decisions were later superseded in code; the plan itself records this in a note of 11 September.

Table B.3: Locked decisions D1–D20 of the backend skeleton plan and their state on 26 October.

| # | Decision | State |
| --- | --- | --- |
| D1 | COLA multi-module backend: client, adapter, application, domain, infrastructure, bootstrap | In force (section 3.1) |
| D2 | Group `com.blocbuy`, artifact prefix `blocbuy-` | In force |
| D3 | Backend self-contained under `backend/`, no repository-root build file | In force |
| D4 | Bounded contexts identity, groupbuy, order, catalog, notification; payment added 31 Aug | In force |
| D5 | PostgreSQL 18 and Redis 8 only; no message broker or search engine | Amended 11 Oct by backend ADR 0007 (SNS and SQS for cross-service events only) |
| D6 | Sa-Token session stored in Redis | In force |
| D7 | One token domain; RBAC roles and permissions loaded from SQL tables | In force; amended for the SWE5001 topology by backend ADR 0008 |
| D8 | Database `Blocbuy`, schema `domain` | In force |
| D9 | Hand-written converters, no MapStruct | In force |
| D10 | Errors as RFC 9457 ProblemDetail; success as a bare DTO | Superseded by the `Result<T>` envelope (skeleton plan historical note, 11 Sep 2026); the client side is frontend ADR 0009 |
| D11 | API base path `/api/v1` | In force |
| D12 | springdoc generates `contracts/openapi.json` | In force |
| D13 | Checkstyle with Google checks, 120-column lines | In force |
| D14 | No formatter, Checkstyle only | Superseded by Spotless with google-java-format |
| D15 | English messages first, with an i18n seam in `ErrorCode` | In force |
| D16 | Dockerfile and Compose as a later task | Done (`backend/Dockerfile`, `deployment/backend/compose.yml`) |
| D17 | Rotate leaked `dev` database passwords | One-off action |
| D18 | Spring Boot pinned at 3.5.15 | Superseded by Spring Boot 4.1.1 |
| D19 | Commit an empty-baseline OpenAPI contract | Done |
| D20 | One Testcontainers PostgreSQL smoke test in the skeleton | Done, since grown into the integration test suite |

#### B.4 Significant decisions without an ADR

The decisions below shape the design described in chapter 3 but have no numbered ADR. They were made in pull requests, design notes or the skeleton plan. They are stated here rather than in back-dated ADRs; each row gives the choice, the reason and where the reasoning can be found.

Table B.4: Significant decisions that have no ADR.

| Decision, choice and reason | Alternative it replaced | Where it is recorded |
| --- | --- | --- |
| **Authentication.** A server-side Sa-Token session in Redis, sent as an HttpOnly cookie. A session can be revoked at once, and the token never reaches JavaScript | JWT in browser local storage after Google sign-in (proposal); Google sign-in itself is unchanged | Skeleton plan D6, D7 (30 August, before the first sprint); frontend ADR 0002 covers only the cookie and same-origin side |
| **Persistence framework.** MyBatis with hand-written XML SQL. The design depends on explicit `SELECT … FOR UPDATE`, guarded updates and partial unique indexes, which are easier to see and test in plain SQL | Spring Data JPA (proposal); MyBatis sits behind the domain's repository ports | Skeleton plan D1, D9 (30 August, before the first sprint); section 3.2.4 |
| **Framework-free domain.** The domain module uses only the JDK and Lombok, enforced by ArchUnit, so domain rules are unit-tested without Spring | Annotated entities in the domain | Skeleton plan (Table B.3); Table 3.2 |
| **Database conventions.** UUIDv7 keys, table prefixes, soft delete, no foreign keys or CHECK constraints (section 3.6) | Database-enforced references | The team's `pgsql-ddl` skill and the migration headers; backend ADR 0008 states the reason that applies on SWE5001 |
| **Concurrency approach.** Pessimistic row locks in the order of Table 3.12 (section 3.2.8), guarded updates, unique indexes as a backstop (index list in section 3.6) and optimistic version checks (Optimistic Offline Lock, section 3.5.5) | Optimistic locking everywhere | Backend ADRs 0001 and 0002; issue #663 (open issues in Table 2.6) |
| **Team lifecycle.** The team's states are an enum with one transition rule (`DealStatus.canTransitionTo`), not GoF State (reasoning in section 3.5.5) | GoF State, as planned in the proposal and the skeleton plan | Code; section 3.4.5 |
| **Dependency scanning.** Trivy scans Maven and npm dependencies; OWASP Dependency-Check is not used, because Trivy covers the same CVEs without an NVD API key | OWASP Dependency-Check (proposal §5.2) | `docs/quality/quality-gates.md` (deliberate non-gates) |
| **Required checks.** Trivy's dependency and infrastructure-as-code scans run but are not among the six required checks (Table 4.2) | All scans blocking | Repository ruleset; section 4.2 |
| **CI cloud access.** GitHub OIDC with one role per job on `dev`: ECR push, EC2 deploy and frontend deploy | Plan, deploy and apply roles (proposal §5.2) | Workflows `ci-backend.yml`, `ci-frontend.yml`; section 4.1 |
| **Secret storage.** SSM Parameter Store SecureString parameters hold the secrets of the SWE5001 stack and of the unapplied production design; dev keeps its secrets in a root-only environment file on the host. Parameter Store is free at the standard tier, while Secrets Manager adds cost and a seven-day deletion window | AWS Secrets Manager (proposal §5.2, §6.3) | SWE5001 microservice refactoring plan (secrets row); `deployment/backend/bin/fetch-env.sh` |
| **Static credentials.** One IAM user access key in the project account, disclosed and accepted (CI authentication, section 4.1; open issues in Table 2.6) | "No static credentials" (proposal §5.2) | `docs/deploy/aws-account-governance/README.md` |
