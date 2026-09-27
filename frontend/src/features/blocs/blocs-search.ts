import * as v from "valibot";

import { ordersSearchDefaults, ordersSearchSchema } from "@/features/order/orders-search";

// `/account?blocs=` picks the My blocs chip (§4.4). Active is the default and is left out of the
// URL; an unknown value falls back to it instead of failing the route. `/account` validates the
// bloc filter and the order-history paging together, so the two schemas merge here.
const blocsSearchSchema = v.object({
  blocs: v.optional(
    v.fallback(v.picklist(["active", "completed", "refunded"]), "active"),
    "active",
  ),
});

export const accountSearchSchema = v.object({
  ...ordersSearchSchema.entries,
  ...blocsSearchSchema.entries,
});

// Left out of the URL by the route's `stripSearchParams`, so `/account` stays `/account`.
export const accountSearchDefaults = { ...ordersSearchDefaults, blocs: "active" as const };
