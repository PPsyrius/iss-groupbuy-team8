import * as v from "valibot";

// `/payments/return?paymentId=<uuid>`: the Stripe return hop. The id is only a reference the
// route uses to redirect to the receipt; ownership is enforced there, server-side. Older local
// Checkout links carry no parameter and fall back to `lastPaymentKey` in the route. A malformed
// value is dropped, not thrown: `validateSearch` must not reach the route's error card before
// the route has tried that fallback.
// No exported `InferOutput` type: the route reads `search.paymentId` through the router's own
// inference, and knip fails `pnpm check` on an unused export. Add one when a consumer appears.
export const paymentsReturnSearchSchema = v.object({
  paymentId: v.fallback(v.optional(v.pipe(v.string(), v.uuid())), undefined),
});
