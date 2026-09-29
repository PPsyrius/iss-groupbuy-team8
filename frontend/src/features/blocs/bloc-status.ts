import type { MyTeamResponse } from "@/api/generated/types.gen";
import {
  formatCountdown,
  hasEnded,
  parseDeadline,
  type Countdown,
} from "@/features/catalog/deal-time";

// My blocs (UC8, #31; design-screens §4.4): which chip filter a bloc belongs to and what its row
// chip says. The wire `status` is the deal's lowercase code (open, succeeded, expired); the
// contract types it as a plain string, so upper case and the failed / canceled codes are accepted
// too. An unknown code stays under Active, where the buyer looks first. No refund data reaches
// this endpoint, so a closed bloc is "Closed", never "Refunded" (plan §12 note 50).
export type BlocFilter = "active" | "completed" | "refunded";

export const blocFilters: { value: BlocFilter; label: string }[] = [
  { value: "active", label: "Active" },
  { value: "completed", label: "Completed" },
  { value: "refunded", label: "Refunded" },
];

const closedCodes = new Set(["expired", "failed", "canceled", "cancelled"]);

export function blocFilter(status: string | undefined): BlocFilter {
  const code = status?.toLowerCase() ?? "";
  if (code === "succeeded") return "completed";
  if (closedCodes.has(code)) return "refunded";
  return "active";
}

export function blocCounts(teams: readonly MyTeamResponse[]): Record<BlocFilter, number> {
  const counts = { active: 0, completed: 0, refunded: 0 };
  for (const team of teams) counts[blocFilter(team.status)] += 1;
  return counts;
}

export interface BlocChip {
  label: string;
  color: "default" | "success" | "warning";
}

// An open bloc past its deadline waits for the sweeper to close it; until then it is "Ended".
export function blocChip(team: MyTeamResponse, nowMs: number): BlocChip {
  const filter = blocFilter(team.status);
  if (filter === "completed") return { label: "Filled", color: "success" };
  if (filter === "refunded") return { label: "Closed", color: "default" };
  if (hasEnded(team.expireTime, nowMs)) return { label: "Ended", color: "default" };
  return blocCountdown(team, nowMs)?.urgent
    ? { label: "Closing soon", color: "warning" }
    : { label: "Filling", color: "default" };
}

// The artboard's row countdown: "1d 4h left" from a day out, otherwise the listing's
// "5h 12m left" / urgent "52:09 left" (`formatCountdown`). Null without a readable deadline.
export function blocCountdown(team: MyTeamResponse, nowMs: number): Countdown | null {
  const deadline = parseDeadline(team.expireTime);
  if (deadline === null) return null;
  const ms = deadline - nowMs;
  if (ms < 86_400_000) return formatCountdown(deadline, nowMs);
  const hours = Math.floor(ms / 3_600_000);
  return { text: `${Math.floor(hours / 24)}d ${hours % 24}h left`, urgent: false };
}
