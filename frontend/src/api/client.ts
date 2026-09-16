import type { QueryClient } from "@tanstack/react-query";

import { ApiError } from "./envelope";
import { meQueryKey } from "./generated/@tanstack/react-query.gen";
import { client } from "./generated/client.gen";

// Non-2xx: throw an ApiError with the envelope's code/message when the body is an envelope.
// 401 additionally drops the session query so `useSession` re-resolves to anonymous.
// Never redirects: only route `beforeLoad` guards navigate.
export function installAuthInterceptor(queryClient: QueryClient) {
  client.interceptors.response.use(async (response) => {
    if (response.ok) return response;
    let body: { code?: string; message?: string; traceId?: string } = {};
    try {
      const parsed: unknown = await response.clone().json();
      if (parsed && typeof parsed === "object") {
        body = parsed;
      }
    } catch {
      // non-JSON error body (e.g. ALB 403); fall through with defaults
    }
    if (response.status === 401) {
      queryClient.removeQueries({ queryKey: meQueryKey() });
    }
    throw new ApiError({
      code: body.code ?? `HTTP_${response.status}`,
      message: body.message ?? response.statusText,
      status: response.status,
      ...(body.traceId === undefined ? {} : { traceId: body.traceId }),
    });
  });
}
