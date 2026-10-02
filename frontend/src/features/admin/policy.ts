import { ApiError, conflictVersion } from "@/api/envelope";
import { formatDay, parseDeadline } from "@/features/catalog/deal-time";

import { type Refusal, refusalOf } from "./listing-errors";

// The bloc price rule panel's state (admin plan A2-01, artboard 4b.5). The backend applies only
// the latest revision, which replaces every earlier one with no fallback, so the latest rule's
// window alone says whether buyers see a bloc price now. `validFrom` is inclusive and
// `validUntil` exclusive (§6 Q13).
export type RuleTiming = "none" | "scheduled" | "active" | "expired";

// A rule is configured only on a listing that is on sale (BA-11): a draft and an off-sale listing
// show why instead of the form.
export type PolicyState = RuleTiming | "unavailable-draft" | "unavailable-off-sale";

type Rule = Readonly<{ validFrom?: string | undefined; validUntil?: string | undefined }>;

const LAST_MINUTE = 60_000;

export function ruleTiming(rule: Rule | null | undefined, nowMs: number): RuleTiming {
  if (!rule) return "none";
  const from = parseDeadline(rule.validFrom);
  const until = parseDeadline(rule.validUntil);
  if (from !== null && nowMs < from) return "scheduled";
  return until !== null && nowMs >= until ? "expired" : "active";
}

export function policyState(
  rule: Rule | null | undefined,
  listingStatus: string | undefined,
  nowMs: number,
): PolicyState {
  const status = listingStatus?.toLowerCase();
  if (status === "draft") return "unavailable-draft";
  if (status === "off_sale") return "unavailable-off-sale";
  return ruleTiming(rule, nowMs);
}

// The 4b.5 chip. The expired day is the day of the last valid minute, as on the table's line.
export function ruleChip(timing: RuleTiming, rule: Rule | null | undefined): string {
  if (timing === "none") return "No rule yet";
  if (timing === "active") return "Active";
  if (timing === "expired") {
    const until = parseDeadline(rule?.validUntil);
    return until === null ? "Expired" : `Expired ${formatDay(until - LAST_MINUTE)}`;
  }
  const from = parseDeadline(rule?.validFrom);
  return from === null ? "Scheduled" : `Starts ${formatDay(from)}`;
}

// The replace-now warning (A2-01 step 5, §11 D3): a revision that starts later still replaces the
// active rule at once, so buyers lose the bloc price until it starts.
export function replacesActiveNow(
  timing: RuleTiming,
  validFromMs: number | null,
  nowMs: number,
): boolean {
  return timing === "active" && validFromMs !== null && validFromMs > nowMs;
}

// The policy read answers 409 `CATALOG_409_001`, not 404, when the listing has no rule yet.
export function isNoRule(error: unknown): boolean {
  return error instanceof ApiError && error.code === "CATALOG_409_001";
}

// What a refused rule write means for the panel. A stale policy version is the panel's own
// conflict; `CATALOG_409_001` on a write means the listing is no longer on sale. The rest reads as
// a listing write does, with the field paths `amount`, `validFrom` and `validUntil` (#323).
export type PolicyRefusal = Refusal | { kind: "unavailable" };

export function policyRefusalOf(error: unknown): PolicyRefusal {
  if (error instanceof ApiError) {
    if (error.code === "CATALOG_412_001")
      return { kind: "conflict", serverVersion: conflictVersion(error) };
    if (error.code === "CATALOG_409_001") return { kind: "unavailable" };
  }
  return refusalOf(error, "The server refused this rule. Check the fields and try again.");
}
