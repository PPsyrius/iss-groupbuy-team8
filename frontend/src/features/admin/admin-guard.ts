import type { QueryClient } from "@tanstack/react-query";

import { ApiError } from "@/api/envelope";

import { adminListingsOptions } from "./admin-api";

// `me` carries no roles (CR-9, BA-1), so admin access is decided by asking: the probe is the
// "All" count query (`size=1`) that the dashboard and the tabs reuse, cached for 30 seconds so
// moving around the console does not re-ask on every navigation (admin plan §12 note 2). No
// retries: a 403 is a definite answer, and any other failure gets the card's Try again.
export class AdminAccessDenied extends Error {
  constructor() {
    super("Admin access denied");
    this.name = "AdminAccessDenied";
  }
}

export async function requireAdmin(queryClient: QueryClient) {
  try {
    await queryClient.query({
      ...adminListingsOptions({ page: 1, size: 1 }),
      staleTime: 30_000,
      retry: false,
    });
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) throw new AdminAccessDenied();
    throw e;
  }
}
