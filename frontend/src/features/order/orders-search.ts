import * as v from "valibot";

// `/account` order-history paging. Mirrors the contract's bounds on GET /api/v1/orders/me:
// page >= 1, size 1..100. Ten per page keeps the list short on a phone; out-of-range or
// non-numeric values fall back instead of failing the route, as on the catalog.
const DEFAULT_PAGE_SIZE = 10;

// Left out of the URL by the route's `stripSearchParams`, so `/account` stays `/account`.
export const ordersSearchDefaults = { page: 1, size: DEFAULT_PAGE_SIZE };

export const ordersSearchSchema = v.object({
  page: v.optional(v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1)), 1), 1),
  size: v.optional(
    v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(100)), DEFAULT_PAGE_SIZE),
    DEFAULT_PAGE_SIZE,
  ),
});

export type OrdersSearch = v.InferOutput<typeof ordersSearchSchema>;
