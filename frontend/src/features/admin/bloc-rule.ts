import type { AdminListingResponse } from "@/api/generated/types.gen";
import { formatDay, parseDeadline } from "@/features/catalog/deal-time";

import { formatSgd } from "./money";

// The line under each title in the 4a.2 table, from the listing's latest bloc price rule (#293,
// BA-4). The backend applies only the latest revision, so an expired or future latest rule means
// no bloc price today, and the line says which. `validFrom` is inclusive and `validUntil`
// exclusive; the last valid day is the day of the final minute (admin plan §6 Q13), so a rule
// ending at 1 Oct 00:00 SGT is "valid to 30 Sep". Days are Singapore days.
const LAST_MINUTE = 60_000;

export function blocRuleLine(
  listing: Readonly<Pick<AdminListingResponse, "status" | "latestPolicy">>,
  nowMs: number,
): string {
  if (listing.status?.toLowerCase() === "draft") return "Draft · not published";
  const rule = listing.latestPolicy;
  if (!rule?.amount) return "No bloc price rule yet";
  const price = `Bloc price ${formatSgd(rule.amount)}`;
  const from = parseDeadline(rule.validFrom);
  const until = parseDeadline(rule.validUntil);
  if (from !== null && nowMs < from) return `${price} · starts ${formatDay(from)}`;
  if (until === null) return price;
  const lastDay = formatDay(until - LAST_MINUTE);
  return nowMs < until ? `${price} · valid to ${lastDay}` : `${price} · expired ${lastDay}`;
}
