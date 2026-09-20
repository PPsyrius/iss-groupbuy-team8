# PR #126: payment quality fixes

Owner: Yorick. Date: 20 September 2026. NFR-06 and NFR-20.

The SonarCloud analysis of `86863a8` reported 39.6% new-code coverage,
reliability C and 54 open findings. The PostgreSQL payment tests were conditional
on a local environment variable, so CI did not execute them.

## Changes

- Run the two PostgreSQL payment suites without an environment-based skip.
  CI starts isolated PostgreSQL 18 containers. The local test script retains its
  guarded private cluster and now runs Maven verify, including aggregate JaCoCo.
- Split settlement and recovery command handling into smaller methods. Remove
  the unused exact-conversion call, extract repeated constants, and make failure
  assertions unambiguous and non-vacuous.
- Add tests for quote limits, ownership, provider fact mismatches, uncertain
  Checkout creation, compensation, recovery, refunds and operator commands.
- Set receipt responses to `Cache-Control: no-store` and scope receipt query
  caches to the signed-in user. Test switching accounts without clearing caches.
- Correct the local environment example's profile and registered return route.

## Local verification

`bash scripts/test-stripe-sprint2.sh` passed: 725 tests, zero failures, errors or
skips. PostgreSQL was real and isolated; Stripe network calls were mocked.
The build passed Spotless, Checkstyle, SpotBugs and architecture checks.

The aggregate report at
`backend/blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml` recorded:

| Class | Lines | Branches |
|---|---:|---:|
| StripeCheckoutService | 289/289 | 213/220 |
| RefundService | 64/64 | 46/46 |
| StripeOperationsConfiguration | 70/70 | 29/29 |

Frontend checks passed 127 tests; the production Vite build passed.
The source/contract guard passed for 192 original files, 13 HTTP paths and
23 schemas. No shared configuration, deployment setting, original interface,
coverage exclusion or quality threshold was changed.

These are local results, not a claim of deployed Stripe acceptance or the
SonarCloud new-code percentage. The updated PR must pass its own CI analysis;
the container-backed path is verified there. Team review is still required.
