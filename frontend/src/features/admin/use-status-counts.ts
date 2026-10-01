import { useQuery } from "@tanstack/react-query";

import { adminCountsOptions } from "./admin-api";
import { isAdminDenied } from "./AdminShell";
import type { ListingTab } from "./listings-search";

// One `counts` read (#290, admin plan A3-02) gives every tab its badge. All is the three statuses
// summed: the backend counts the same non-deleted rows the page filters use. A field the server
// leaves out reads as 0. A 403 (a role revoked mid-session) is rethrown to reach Access denied;
// any other failure leaves the tabs without counts.
export function useStatusCounts(): Record<ListingTab, number> | undefined {
  const { data, error } = useQuery(adminCountsOptions());
  if (isAdminDenied(error)) throw error;
  if (!data) return undefined;
  const draft = data.draft ?? 0;
  const onSale = data.onSale ?? 0;
  const offSale = data.offSale ?? 0;
  return { all: draft + onSale + offSale, draft, "on-sale": onSale, "off-sale": offSale };
}
