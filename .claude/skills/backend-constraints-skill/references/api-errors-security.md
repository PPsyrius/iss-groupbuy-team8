# REST, Error Codes, and Sa-Token

Read this when handling HTTP interfaces, exceptions, authentication, permissions, or external input.

## REST API

- Base paths use `/api/v1`; resources use lowercase plural nouns with necessary hierarchy, e.g. `GET /api/v1/orders/{orderId}`, `POST /api/v1/group-deals/{dealId}/participants`.
- HTTP method semantics: GET queries without side effects; POST creates / non-idempotent actions; PUT fully replaces and is idempotent; PATCH applies partial changes; DELETE idempotently deletes. Genuine business actions may use sub-resources or action endpoints, such as `POST /orders/{id}/cancellations`; avoid `/cancelOrder`.
- Create success returns `201 Created` with `Location`; asynchronous acceptance returns `202 Accepted`; successful no-body responses return `204 No Content`; validation errors 400; unauthenticated 401; forbidden 403; not found 404; state conflict/duplicate 409; failed concurrency precondition 412; rate limited 429.
- Do not wrap every failure in HTTP 200. Errors use a unified Problem Details style structure; success responses may return DTOs directly - if the project already has a unified success envelope, keep it consistent but do not change HTTP status semantics.
- Path identifies resources; Query is for filtering, sorting, and pagination. Fields, sort keys, and include/expand must be whitelisted.
- Lists must be paginated with a maximum page size; for deep pagination prefer stable sorting + cursor/keyset, with the response indicating `nextCursor`. Unbounded full-list queries must not be offered.
- Times use ISO-8601 including timezone/offset; internally the server represents instants with `Instant`. Monetary amounts travel as decimal strings or smallest currency units with an explicit currency; JSON floating-point error is forbidden.
- DTOs are isolated from domain objects/DOs; request DTOs use Bean Validation. Distinguish field-format validation, cross-field validation, and domain invariants.
- Write interfaces define idempotency. Order placement, payment, refund, and callbacks use an `Idempotency-Key` or a stable business request number; persist the request fingerprint and result; the same key with a different request body returns a conflict.
- Resources that support concurrent modification use a version number/ETag or business version; never silently overwrite concurrent updates.
- OpenAPI documents paths, status codes, authentication, pagination, idempotency, field constraints, and examples; contract changes must be backward compatible or version-bumped.

## Error Code and Business Exception Baseline

Error codes must be stable, searchable, and observable; dynamic text must never serve as a machine code. Recommended minimal abstraction:

```java
public interface ErrorCode {
    String code();
    HttpStatus httpStatus();
    String defaultMessage();
}

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("ORDER_404_001", HttpStatus.NOT_FOUND, "Order not found"),
    ORDER_STATE_CONFLICT("ORDER_409_001", HttpStatus.CONFLICT, "Order state does not allow this operation");
    // fields + constructor + interface methods
}

public final class BizException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    // constructible only from an ErrorCode (plus safe structured details)
}
```

- Every business domain maintains its own enum; once published, codes are never reused or redefined. Messages may be localized; machine codes may not.
- `BizException` represents only expected business rejections; database unavailability, serialization failures, and null pointers are system exceptions.
- If the repository lacks an equivalent system, you must first report "missing error-code enum system", recommend building it as the top priority, and only then add business exceptions. A temporary `throw new BizException("insufficient stock")` is forbidden.
- Do not use exceptions to replace normal branches; batch interfaces must define all-or-nothing or per-item result semantics explicitly.

## Global Exception Handling Baseline

There must be a single main entry point `@RestControllerAdvice` handling at least:

- `BizException`: use the enum's HTTP status and business code;
- `MethodArgumentNotValidException`, `ConstraintViolationException`, argument resolution exceptions: 400 with safe field errors;
- Sa-Token not-logged-in / no-permission exceptions: 401/403;
- Project exceptions for not-found, conflict, and rate limiting;
- Unknown `Exception`: log one ERROR with traceId; clients only receive a generic 500.

Suggested error body:

```json
{
  "type": "https://blocbuy.example/problems/order-state-conflict",
  "title": "Order state conflict",
  "status": 409,
  "code": "ORDER_409_001",
  "detail": "Order state does not allow this operation",
  "instance": "/api/v1/orders/123/cancellations",
  "traceId": "...",
  "errors": []
}
```

- `detail`/`errors` must not contain SQL, stack traces, internal paths, raw third-party responses, or sensitive information.
- Expected 4xx usually does not log an ERROR stack; unknown 5xx is logged exactly once at the boundary. Repository, Service, and Controller must not repeatedly log the same exception at multiple layers.
- If there is no global handler, you must report "missing global exception handling" and recommend building it first; ad-hoc catch-all handling in each Controller is not allowed.

## Unified Sa-Token Authentication

- Use Sa-Token for login, logout, sessions, roles, and permission checks; centrally configure token name, validity, concurrent login, renewal, Cookie/header policy, and excluded paths.
- Controller/route entry points may declare coarse-grained permissions with `@SaCheckLogin`, `@SaCheckPermission`, etc.; application services must still verify object-level permissions such as resource ownership, merchant tenant, and order owner, to prevent IDOR.
- Permission identifiers use stable naming such as `order:read`, `order:cancel`, `merchant:product:write`; vague `admin` branches scattered through business code are forbidden.
- User/tenant IDs in the login state come from the trusted context; trusting an ownerId/merchantId passed in the request body is forbidden. Cross-tenant queries must always carry an enforced tenant condition.
- Clearly separate admin-side and user-side token domains, account types, or login systems to avoid token cross-use; high-risk operations require secondary verification and auditing.
- Tokens travel only over TLS; Cookie mode configures Secure, HttpOnly, SameSite and handles CSRF; header mode configures strict CORS. Never output tokens in logs, URLs, or exception bodies.
- Configure layered rate limiting and anti-replay for login, SMS verification codes, password reset, payment callbacks, search, and similar endpoints. Verification codes are single-use, short-TTL, and failure-count limited.
- Passwords use a reviewed adaptive hash (e.g. Argon2id/bcrypt) with a per-user random salt; reversible encryption or generic fast hashes are forbidden. Secrets come from secret management/environment injection and support rotation.
- All payment callbacks verify the signature first, then match merchant number, order number, amount, currency, and time window; use unique constraints for idempotency and complete the necessary persistence before responding.
