import * as v from "valibot";

// Mirrors the contract's bounds on GET /api/v1/products: page >= 1, size 1..100. The default page
// size is 20, as the Catalog artboard and the design brief §8 state (plan §12 note 39).
// Out-of-range or non-numeric values fall back instead of failing the route, so a hand-edited
// URL still renders. An empty `category` (`?category=`) becomes "no filter", not an error.
const DEFAULT_PAGE_SIZE = 20;

export const productsSearchSchema = v.object({
  page: v.optional(v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1)), 1), 1),
  size: v.optional(
    v.fallback(v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(100)), DEFAULT_PAGE_SIZE),
    DEFAULT_PAGE_SIZE,
  ),
  category: v.optional(
    v.pipe(
      v.string(),
      v.trim(),
      v.transform((s) => (s.length > 0 ? s : undefined)),
    ),
  ),
});

export type ProductsSearch = v.InferOutput<typeof productsSearchSchema>;
