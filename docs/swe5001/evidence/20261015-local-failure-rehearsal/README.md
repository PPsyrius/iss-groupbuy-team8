# Local identity-outage and poison-message rehearsal

Date: 2026-10-15. Owner: @Yorick-Yu. Refs #459; the issue stays open for the AWS session. Production-code baseline: `7ddffaee0c3a3c3b41a9716d2e9b27456a0e1598` plus the test-only changes in this PR.

## Local result

The initial focused Docker-backed Maven verification passed 72 tests with zero failures, errors or skips: 35 `RemoteUserAppServiceTest`, 34 `StripeCheckoutFlowTest`, one `OutboxRelayElasticMqIT`, and the two rehearsal integration tests below. The initial attempt rejected an inconsistent local Checkout fixture; its expiry was changed to the saved payment expiry and its return value now uses the existing Stripe Checkout host allowlist. No production validation was relaxed.

After making evidence output portable, the complete default Docker-backed `./mvnw -B -ntp verify` returned `BUILD SUCCESS` in 3 minutes 37 seconds on JDK 21. Both new tests ran without optional rehearsal properties and passed; all eleven reactor modules succeeded, including formatting, static analysis, architecture and integration checks. The existing opt-in `PerformanceBaselineIT` was skipped. The bootstrap unit-test fork emitted a scheduler-shutdown/fork-exit timeout diagnostic after its tests completed; Maven continued and returned exit 0. Raw local logs remain outside git; generated per-test JSON stays in ignored `target/` directories.

| Scenario | Observed behavior | Evidence boundary |
| --- | --- | --- |
| Healthy purchase | START and Checkout return 200 through the existing API | Real commerce HTTP, PostgreSQL 18 and Redis 8.10; local identity and Stripe provider substitutes |
| Identity HTTP server stopped | Browse returns 200; START and a correctly signed webhook return 500 | The same commerce process stays up; the identity substitute's listening socket is actually stopped |
| Webhook rollback | Payment remains `created`; no event receipt, participation receipt, membership, order or refund survives | Real Flyway schema, business services and database transaction |
| Identity restored | Redelivery returns 200 and payment becomes `succeeded`; a duplicate redelivery leaves one event, membership and order, and zero refunds | Real Stripe SDK HMAC verification with a fixture signing value; provider retrieval is a substitute, not Stripe Sandbox |
| Poison envelope | Receive counts are 1, 2, 3, 4 and 5, then the broker moves the malformed envelope to the DLQ | Real ElasticMQ SQS API and checked-in redrive policy; a test decoder rejects the message without deleting it |
| Valid message after poison | Production `SqsOutboxEventTransport` publishes a valid event, which the test decodes and acknowledges | Does not exercise the production notification consumer or SNS routing |

The queue test keeps `maxReceiveCount=5`, sets local visibility to one second and immediately releases each failed receipt for the next delivery. It verifies the receive/redrive threshold, not production retry spacing. All containers are disposable and stopped by Testcontainers. No AWS resources or real Stripe credentials are used.

## Reproduce

Use JDK 21 and a running Docker daemon. Run from `backend/`; no personal filesystem paths, credentials or extra Maven properties are required:

```bash
./mvnw -B -ntp -pl services/commerce-svc -am verify \
  -Dtest=RemoteUserAppServiceTest,StripeCheckoutFlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=OutboxRelayElasticMqIT,LocalPoisonMessageRehearsalIT,LocalIdentityOutageRehearsalIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

`LocalIdentityOutageRehearsalIT` and `LocalPoisonMessageRehearsalIT` also run during normal `./mvnw verify`. Each writes a result under its module's ignored `target/rehearsal-evidence/` directory. An optional `-Drehearsal.evidence=<directory>` selects a shared evidence directory; `-Drehearsal.baseline=<sha>` records a caller-supplied baseline in the identity result, otherwise it says `not-recorded`. Neither option changes production runtime configuration.

## AWS acceptance still pending

- V5: use the #458 measured knee, keep two ECS commerce tasks, stop one at 50% knee load and capture zero non-2xx plus ECS task replacement events.
- Identity outage: repeat the browse/START/webhook recovery scenario on the actual deployed services and retain the session evidence.
- V7: exercise the real notification consumer, capture poison-message redrive after five receives, and capture the CloudWatch DLQ alarm; local decode/redrive is only preparation.
- V8: record create, smoke and destroy durations from existing promotion/session runs, without starting an extra deployment cycle.
- V6 remains a report explanation of the existing circuit-breaker configuration; the October 11 rescope removed its live broken-image drill.

The local result does not satisfy #459's shared-AWS acceptance criteria. Existing HTTP APIs, runtime addresses, feature switches, migrations and secrets are unchanged.
