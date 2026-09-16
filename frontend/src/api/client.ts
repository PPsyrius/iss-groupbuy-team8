import type { QueryClient } from "@tanstack/react-query";

import { ApiError, OK_CODE } from "./envelope";
import { meQueryKey } from "./generated/@tanstack/react-query.gen";
import { client } from "./generated/client.gen";

// Non-2xx: throw an ApiError with the envelope's code/message/traceId when the response body is
// an object and each field is present as a string (an unsound body should not smuggle non-string
// values into the error). 401 additionally settles the `me` query on an anonymous OK envelope
// (never removes/invalidates/resets it: the `me` request's own 401 would just loop), so
// `unwrapResult` resolves to `undefined` and the header falls back to the login link without the
// query being left pending or refetching on every later root re-render. Because this interceptor
// throws on every non-2xx response, the generated client's `throwOnError: false` errors-as-values
// mode is unreachable for any caller that goes through it.
// Never redirects: only route `beforeLoad` guards navigate.
export function installAuthInterceptor(queryClient: QueryClient) {
  client.interceptors.response.use(async (response) => {
    if (response.ok) return response;
    let body: { code?: string; message?: string; traceId?: string } = {};
    try {
      const parsed: unknown = await response.clone().json();
      if (parsed && typeof parsed === "object") {
        // Read-only probe of an untrusted body; every field below is re-checked with `typeof`
        // before use, so the assertion only grants property access, not trust.
        // oxlint-disable-next-line typescript/no-unsafe-type-assertion
        const candidate = parsed as Record<string, unknown>;
        body = {
          ...(typeof candidate.code === "string" ? { code: candidate.code } : {}),
          ...(typeof candidate.message === "string" ? { message: candidate.message } : {}),
          ...(typeof candidate.traceId === "string" ? { traceId: candidate.traceId } : {}),
        };
      }
    } catch {
      // non-JSON error body (e.g. ALB 403); fall through with defaults
    }
    if (response.status === 401) {
      // Anonymous OK envelope: `data` stays absent so `unwrapResult` yields `undefined`. The
      // generated `meQueryKey()` return type is not data-tagged, so this stays untyped against
      // the react-query overload that infers from a `DataTag`-branded key; the object literal
      // still checks structurally against `ResultMeResponse` (all fields optional), no cast
      // needed.
      queryClient.setQueryData(meQueryKey(), { code: OK_CODE, message: "anonymous" });
    }
    throw new ApiError({
      code: body.code ?? `HTTP_${response.status}`,
      message: body.message ?? response.statusText,
      status: response.status,
      ...(body.traceId === undefined ? {} : { traceId: body.traceId }),
    });
  });
}
