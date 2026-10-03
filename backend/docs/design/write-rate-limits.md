# Browser write rate limits

Issue #274 extends the existing #201 `RateLimitGateway`; PostgreSQL purchase admission and payment idempotency remain authoritative.

| Operation | Scope | Subject | Window | Maximum |
|---|---|---|---|---|
| JOIN payment intent | `join-intent` | Authenticated user UUID + deal UUID | 60 seconds | 60 requests |
| Checkout preparation | `payment-initiation` | Authenticated user UUID | 60 seconds | 60 requests |
| Invite creation or reuse | `invite-create` | Authenticated user UUID | 60 seconds | 60 requests |
| Anonymous invite resolution (existing) | `invite-resolve` | Trusted client address | 60 seconds | 60 requests |

The defaults reuse the existing invite-resolution allowance. A window starts on its first request, and every valid request reaching the application service counts, including idempotent retries and later business rejections. JOIN is counted per deal; changing an intent or deal does not bypass the user-wide checkout or invite allowance. START intents, receipt reads and provider webhooks do not consume these write scopes.

The existing Redis Lua script increments and assigns `PEXPIRE` atomically. The established key is `blocbuy:{env}:common:rate-limit:{scope}:{subject digest}`, retaining #201's namespace rather than introducing the older proposed `ratelimit` prefix. The common infrastructure limiter owns the keys; payment and groupbuy own their scope policies. Each key has a 60-second TTL, with at most one key per scope and distinct subject observed during that window. No keys or user lists persist after the window, and raw user, deal and address values are absent from key listings.

An exhausted allowance throws `CommonErrorCode.RATE_LIMITED` before business reads or provider calls; the existing exception handler returns HTTP 429 in the `Result` envelope with `COMMON_429_001`. JOIN and invite allowance checks precede transaction creation, so Redis waits do not occupy database connections. Invite business reads and minting retain their existing 10-second transaction through `TransactionTemplate`. Missing optional payment integrations still report their existing disabled-state error. Authentication, request validation, feature rollout and CSRF marker checks occur before the application service.

Redis exceptions and missing script results allow the request. Each degradation logs the scope and safe cause class and increments `blocbuy.rate_limit.fail_open{scope}`; subjects and exception messages are omitted. Only the fixed application scope names become metric labels. Redis uses the existing command timeout, and failure does not change PostgreSQL locking, uniqueness, transactions or checkout idempotency.

The contract adds 429 responses to `createPaymentIntent` (JOIN only), `createPaymentCheckout` and `createShareInvite`. Existing success payloads and operation IDs remain unchanged. Unit tests preserve delegation and business-error paths; real Redis integration tests verify atomic counting, TTL and reset, while HTTP integration tests verify the three scope boundaries, unified rejection before business work and an unreachable Redis connection's allowed response and degradation metric. The outage tests isolate authenticated sessions in memory, proving limiter degradation after authentication rather than shared-Redis session availability.
