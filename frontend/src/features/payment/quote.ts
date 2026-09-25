// The first-team quote (UC3, plan T3-01) as the sheet shows it. The quote is a preview: START
// re-prices from the latest policy when the intent is created, and Stripe's page carries the
// amount actually charged (ADR 0014 addendum 2026-09-24).
import { ApiError } from "@/api/envelope";
import type { QuoteResponse } from "@/api/generated/types.gen";
import { parseDeadline } from "@/features/catalog/deal-time";

export interface Quote {
  id: string;
  amount: number;
  currency: string;
  expiresMs: number;
}

// CatalogErrorCode.TEAM_PRICE_UNAVAILABLE (HTTP 409): no current policy for this listing, so no
// price exists yet; retrying cannot help until an admin sets one.
const unavailableCode = "CATALOG_409_001";

// Wire amounts are decimal strings ("71.00"); v1 quotes are positive SGD cents only
// (backend/docs/design/team-price-quotes.md), so anything else is a malformed snapshot.
export function readQuote(q: QuoteResponse): Quote | null {
  const amount = Number(q.amount);
  const expiresMs = parseDeadline(q.expiresAt);
  if (!q.id || !Number.isFinite(amount) || amount <= 0 || expiresMs === null) return null;
  return { id: q.id, amount, currency: q.currency ?? "sgd", expiresMs };
}

export function quoteFailure(error: unknown): "unavailable" | "error" {
  return error instanceof ApiError && (error.status === 409 || error.code === unavailableCode)
    ? "unavailable"
    : "error";
}
