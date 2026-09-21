# PR #126 payment quality evidence

Owner: Yorick. Started: 20 September 2026. Updated: 22 September 2026. NFR-06 and NFR-21.

SonarCloud analysis of `86863a8` reported 39.6% new-code coverage, reliability C and 54 findings. PostgreSQL payment tests were conditional on a local environment variable, so CI had not run them.

## Changes

- Run PostgreSQL payment suites against isolated PostgreSQL 18 and include aggregate JaCoCo output.
- Simplify settlement and recovery handling; remove unused conversion and ambiguous assertions.
- Cover quote limits, ownership, provider mismatches, uncertain Checkout creation, compensation, recovery and refund behavior.
- Return receipts with `Cache-Control: no-store` and scope frontend receipt caches by signed-in user.
- Fix Sonar findings including exception-lambda clarity, redundant Mockito verification modes and duplicate/dangling test documentation.

## Verification

The 20 September local script passed 725 backend tests with no failures, errors or skips. It used isolated PostgreSQL with mocked Stripe calls and passed Spotless, Checkstyle, SpotBugs and architecture checks. Frontend checks passed 127 tests and the production build.

The aggregate report recorded:

| Class | Lines | Branches |
| --- | ---: | ---: |
| `StripeCheckoutService` | 289/289 | 213/220 |
| `RefundService` | 64/64 | 46/46 |
| `StripeOperationsConfiguration` | 70/70 | 29/29 |

On 22 September, full Docker-backed Maven verification passed, including 32 Testcontainers integration tests and Flyway V1-V11 on PostgreSQL 18; the aggregate report had no missed lines or partial branches in the 32 touched payment files. Frontend Vitest coverage passed 149 tests: payment features and the touched product/payment routes retained 100% line and branch coverage. TypeScript, Oxlint, Oxfmt and Spotless checks also passed.

The historical source/contract guard passed for 192 original files, 13 HTTP paths and 23 schemas. No coverage exclusion or quality threshold was weakened.

The first CI run (`35506864274`) exposed a test JDBC URL bug: Testcontainers had already appended `?loggerLevel=OFF`, so the schema parameter needed `&`. The helper now chooses the separator from the existing URL. Migrations had succeeded; later connections had selected the wrong schema.

Remote CI and SonarCloud remain the merge gates. These local results do not claim shared deployment.
