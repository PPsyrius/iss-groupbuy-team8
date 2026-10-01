import { useQueries } from "@tanstack/react-query";

import { adminListingsOptions } from "./admin-api";
import { LISTING_TABS, type ListingTab, tabToStatus } from "./listings-search";

// The contract has no count endpoint, so each tab's count is the `total` of a one-row page, the
// "All" tab included (admin plan A1-04 step 4, A3-01); the dashboard reuses all four. A 30s
// staleTime keeps moving between tabs from re-asking.
function statusCountOptions(tab: ListingTab) {
  const status = tabToStatus(tab);
  return {
    ...adminListingsOptions({ page: 1, size: 1, ...(status === undefined ? {} : { status }) }),
    staleTime: 30_000,
  };
}

// `undefined` until every count has arrived, so the tabs never show a partial set.
export function useStatusCounts(): Record<ListingTab, number> | undefined {
  return useQueries({
    queries: LISTING_TABS.map(statusCountOptions),
    combine: ([all, draft, onSale, offSale]) =>
      all?.isSuccess && draft?.isSuccess && onSale?.isSuccess && offSale?.isSuccess
        ? {
            all: all.data.total ?? 0,
            draft: draft.data.total ?? 0,
            "on-sale": onSale.data.total ?? 0,
            "off-sale": offSale.data.total ?? 0,
          }
        : undefined,
  });
}
