import type { ZonedDateTime } from "@internationalized/date";

import type { PolicyRequest, PolicyResponse } from "@/api/generated/types.gen";
import { parseDeadline } from "@/features/catalog/deal-time";

import { sgtDayStart, sgtMinute, toInstant } from "./sgt";

// The revision form's values (admin plan A2-01 step 4): the bloc price stays the decimal string
// typed (§12 note 8), and both ends are Singapore wall times from the minute pickers. `validUntil`
// is the last valid minute, as artboard 4b.4 shows it ("30 Sep 2026, 11.59 pm"); the backend's end
// is exclusive, so the form sends the minute after it (§6 Q13).
export interface PolicyFormValues {
  amount: string;
  validFrom: ZonedDateTime | null;
  validUntil: ZonedDateTime | null;
}

// The exclusive end instant the backend reads, from the last valid minute the picker shows.
export function sentEnd(lastMinute: ZonedDateTime): ZonedDateTime {
  return lastMinute.add({ minutes: 1 });
}

// A new rule starts this minute and runs 31 Singapore days; a revision keeps the latest rule's
// amount, and its end while that is still ahead. Ends are shown as their last valid minute.
const DEFAULT_DAYS = 31;
const MINUTE = 60_000;

export function policyFormDefaults(
  rule: Readonly<Pick<PolicyResponse, "amount" | "validFrom" | "validUntil">> | null | undefined,
  nowMs: number,
): PolicyFormValues {
  const until = parseDeadline(rule?.validUntil);
  const end =
    until !== null && until > nowMs ? until : sgtDayStart(nowMs, DEFAULT_DAYS).toDate().getTime();
  return {
    amount: rule?.amount ?? "",
    validFrom: sgtMinute(nowMs),
    validUntil: sgtMinute(end - MINUTE),
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
    validUntil: toInstant(sentEnd(validUntil)),
    expectedVersion,
  };
}
