import * as v from "valibot";

import { KEYWORD_MAX_LENGTH } from "./filter-form.schema";

// Mirrors the contract's bounds on GET /api/v1/products: page >= 1, size 1..100, keyword up to
// 64 characters (`KEYWORD_MAX_LENGTH`, shared with the form schema). The default page size is
// 20, as the Catalog artboard and the design brief §8 state (plan §12 note 39). Out-of-range or non-numeric values fall back instead of failing the
// route, so a hand-edited URL still renders. An empty `category` or `keyword` (`?keyword=`)
// becomes "no filter", not an error; an over-long keyword is cut to the contract's limit rather
// than rejected (the form never produces one, plan §12 note 43).
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
