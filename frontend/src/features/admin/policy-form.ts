import type { ZonedDateTime } from "@internationalized/date";

import type { PolicyRequest, PolicyResponse } from "@/api/generated/types.gen";
import { parseDeadline } from "@/features/catalog/deal-time";

import { sgtDayStart, sgtMinute, toInstant, toZoned } from "./sgt";

// The revision form's values (admin plan A2-01 step 4): the bloc price stays the decimal string
// typed (§12 note 8), and both ends are Singapore wall times from the minute pickers. `validUntil`
// is the exact, exclusive end instant (§6 Q13): the rule stops at that minute.
export interface PolicyFormValues {
  amount: string;
  validFrom: ZonedDateTime | null;
  validUntil: ZonedDateTime | null;
}

// A new rule starts this minute and runs 31 Singapore days; a revision keeps the latest rule's
// amount, and its end while that is still ahead.
const DEFAULT_DAYS = 31;

export function policyFormDefaults(
  rule: Readonly<Pick<PolicyResponse, "amount" | "validFrom" | "validUntil">> | null | undefined,
  nowMs: number,
): PolicyFormValues {
  const until = parseDeadline(rule?.validUntil);
  return {
    amount: rule?.amount ?? "",
    validFrom: sgtMinute(nowMs),
    validUntil:
      until !== null && until > nowMs
        ? toZoned(rule?.validUntil)
        : sgtDayStart(nowMs, DEFAULT_DAYS),
  };
}

// The schema has already required both ends; a missing one here is a programming error.
export function toPolicyRequest(values: PolicyFormValues, expectedVersion: number): PolicyRequest {
  const { validFrom, validUntil } = values;
  if (!validFrom || !validUntil) throw new Error("A rule needs both ends");
  return {
    amount: values.amount.trim(),
    currency: "sgd",
    validFrom: toInstant(validFrom),
    validUntil: toInstant(validUntil),
    expectedVersion,
  };
}
