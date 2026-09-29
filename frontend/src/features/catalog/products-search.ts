import * as v from "valibot";

import { KEYWORD_MAX_LENGTH } from "./filter-form.schema";

// Mirrors the contract's bounds on GET /api/v1/products: page >= 1, size 1..100, keyword up to
// 64 characters (`KEYWORD_MAX_LENGTH`, shared with the form schema). The default page size is
// 20, as the Catalog artboard and the design brief §8 state (plan §12 note 39). Out-of-range or non-numeric values fall back instead of failing the
// route, so a hand-edited URL still renders. An empty `category` or `keyword` (`?keyword=`)
// becomes "no filter", not an error; an over-long keyword is cut to the contract's limit rather
// than rejected (the form never produces one, plan §12 note 44).
const DEFAULT_PAGE_SIZE = 20;

const optionalTrimmed = (max?: number) =>
  v.optional(
    v.pipe(
      v.string(),
      v.trim(),
      v.transform((s) => (s.length > 0 ? (max === undefined ? s : s.slice(0, max)) : undefined)),
    ),
  );

export const productsSearchSchema = v.object({
  page: v.optional(v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1)), 1), 1),
  size: v.optional(
    v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(100)), DEFAULT_PAGE_SIZE),
    DEFAULT_PAGE_SIZE,
  ),
  category: optionalTrimmed(),
  keyword: optionalTrimmed(KEYWORD_MAX_LENGTH),
});

export type ProductsSearch = v.InferOutput<typeof productsSearchSchema>;

// UC7 invite link on the listing (T3-05, #30): `/products/$productId?deal=…&ref=…`. The link is
// built by the frontend from `POST /group-deals/{dealId}/invites` (backend plan D4, no resolve
// endpoint, D5). `ref` follows `IntentRequest.ref`'s contract pattern; `deal` names the bloc the
// invite is for, since a listing can show a newer bloc by the time the link is opened. A value
// that fails its pattern is never parked, so an invalid ref can never reach the intent.
export const SHARE_REF_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
export const SHARE_DEAL_PATTERN = /^[A-Za-z0-9-]{1,64}$/;

// A supplied value that fails (a number the router parsed, a cut-off or padded value) becomes
// `null`, not `undefined`: the router drops undefined keys, and the route must tell an invalid
// invite link (which empties the parked one) from a plain listing URL.
const optionalMatching = (pattern: RegExp) =>
  v.optional(
    v.pipe(
      v.unknown(),
      v.transform((value) => {
        const s = typeof value === "string" ? value.trim() : "";
        return pattern.test(s) ? s : null;
      }),
    ),
  );

export const listingSearchSchema = v.object({
  deal: optionalMatching(SHARE_DEAL_PATTERN),
  ref: optionalMatching(SHARE_REF_PATTERN),
});
