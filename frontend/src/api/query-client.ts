import { QueryClient } from "@tanstack/react-query";

import { installAuthInterceptor } from "./client";
import { ApiError } from "./envelope";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) =>
        !(error instanceof ApiError && (error.status === 401 || error.status === 404)) &&
        failureCount < 2,
    },
  },
});

installAuthInterceptor(queryClient);
