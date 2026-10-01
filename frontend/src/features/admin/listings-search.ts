import * as v from "valibot";

// `/admin/listings?tab=&page=&size=` (admin plan A1-04). Paging mirrors the contract's bounds on
// GET /api/v1/admin/products (page >= 1, size 1..100); the artboard header reads "page size 20".
// Out-of-range values fall back instead of failing the route, as on the catalog. The tab slugs
// are URL words, not wire codes: `tabToStatus` is the one place that maps them.
const DEFAULT_PAGE_SIZE = 20;

export const LISTING_TABS = ["all", "draft", "on-sale", "off-sale"] as const;
export type ListingTab = (typeof LISTING_TABS)[number];

// Left out of the URL by the route's `stripSearchParams`, so `/admin/listings` stays bare.
export const listingsSearchDefaults = { tab: "all", page: 1, size: DEFAULT_PAGE_SIZE } as const;

export const listingsSearchSchema = v.object({
  tab: v.optional(v.fallback(v.picklist(LISTING_TABS), "all"), "all"),
  page: v.optional(v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1)), 1), 1),
  size: v.optional(
    v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(100)), DEFAULT_PAGE_SIZE),
    DEFAULT_PAGE_SIZE,
  ),
});

export type ListingsSearch = v.InferOutput<typeof listingsSearchSchema>;

const statusByTab = {
  all: undefined,
  draft: "draft",
  "on-sale": "on_sale",
  "off-sale": "off_sale",
} as const satisfies Record<ListingTab, string | undefined>;

export function tabToStatus<T extends ListingTab>(tab: T): (typeof statusByTab)[T] {
  return statusByTab[tab];
}
