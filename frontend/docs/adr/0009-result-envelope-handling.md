# 0009. Result envelope handling

Date: 2026-09-16

## Status

Accepted

## Context

Every backend JSON response is a `Result<T>` envelope. springdoc emits one concrete schema per payload (`ResultMeResponse`, `ResultProductDetailResponse`, `ResultVoid`, …), each with the same five optional properties: `code` (string), `data` (`$ref` to the payload, or `{}` for void), `message` (string), `errors` (untyped) and `traceId` (string); no `required` array, so every generated type has every field optional. The success code is `CommonErrorCode.SUCCESS`, the string `"COMMON_200_000"`, not `"0"` as the brief assumed. Failures arrive either as a non-2xx status with an envelope body (401 from the Sa-Token interceptor via `GlobalExceptionHandler`) or, in principle, as a 200 with a non-success `code`. Components should see payloads, not envelopes, but `traceId` is the only handle support has on a failed request.

## Decision

Generated envelope types are kept as generated; there is no generator transform. One function, `unwrapResult` in `src/api/envelope.ts`, is passed as `select` to every query (or wrapped, as in `select: (r) => unwrapResult(r).items`): it throws `ApiError` (`code`, `message`, `status`, `traceId`) when `code !== OK_CODE`, returns `data`, and for `allowVoid` callers (mutations returning nothing) returns `undefined` as the one deliberate trust boundary. The 401 interceptor throws `ApiError` on every non-2xx response, reading `code`, `message` and `traceId` from the body only when each is present as a string, and falling back to `HTTP_<status>` and the status text (an ALB 403 has no JSON body). `queryClient` defaults `staleTime: 30_000` and does not retry 401 or 404 `ApiError`s. Route loaders map `ApiError.status === 404` to `notFound()` (T2-02).

Alternatives rejected: a generator transform stripping the envelope (hides `traceId` and diverges from the committed contract); unwrapping per call site (duplication, and each site would re-derive the error rules).

2026-09-16: `OK_CODE = 'COMMON_200_000'`, not the brief's `'0'` — confirmed against `backend/blocbuy-domain/.../CommonErrorCode.java` (`SUCCESS("COMMON_200_000", ...)`); `envelope.ts` and `envelope.test.ts` use the real value (T1-07, `48805af`).

## Consequences

Because the interceptor throws on every non-2xx, the generated client's `throwOnError: false` errors-as-values mode is unreachable for callers that go through it; error handling is `ApiError` everywhere. The generated `meQueryKey()` is not `DataTag`-branded, so the interceptor's `setQueryData` call is untyped against that overload and relies on the structural check against `ResultMeResponse` (comment in `client.ts`). `OK_CODE` started module-private (T1-07: knip flags unused exports and nothing consumed it) and was exported on 2026-09-17 (`2d040a2`) for the interceptor's anonymous envelope (ADR 0002). Since all envelope fields are optional, `unwrapResult` must keep its `undefined` fallbacks (`code ?? 'UNKNOWN'`, `message ?? 'Request failed'`); a contract change adding `required` would let those go. If the backend ever starts returning 200 with a failure `code`, the query lands in the error state through `select`, which is the intended path. Coverage: `envelope.test.ts` (3 tests) and `client.test.ts` at `2d040a2`, 7 tests in all.
